package io.agora.education.classroom;

import android.graphics.Rect;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;
import com.herewhite.sdk.domain.GlobalState;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import butterknife.BindView;
import butterknife.OnClick;
import io.agora.base.callback.ThrowableCallback;
import io.agora.base.network.RetrofitManager;
import io.agora.education.EduApplication;
import io.agora.education.R;
import io.agora.education.api.EduCallback;
import io.agora.education.api.message.EduChatMsg;
import io.agora.education.api.message.EduChatMsgType;
import io.agora.education.api.message.EduMsg;
import io.agora.education.api.room.EduRoom;
import io.agora.education.api.room.data.EduRoomInfo;
import io.agora.education.api.room.data.EduRoomState;
import io.agora.education.api.room.data.EduRoomStatus;
import io.agora.education.api.room.data.RoomCreateOptions;
import io.agora.education.api.room.data.EduRoomChangeType;
import io.agora.education.api.room.data.RoomType;
import io.agora.education.api.statistics.AgoraError;
import io.agora.education.api.statistics.ConnectionState;
import io.agora.education.api.statistics.NetworkQuality;
import io.agora.education.api.stream.data.EduStreamEvent;
import io.agora.education.api.stream.data.EduStreamInfo;
import io.agora.education.api.stream.data.EduStreamStateChangeType;
import io.agora.education.api.user.EduStudent;
import io.agora.education.api.user.data.EduBaseUserInfo;
import io.agora.education.api.user.data.EduUserEvent;
import io.agora.education.api.user.data.EduUserInfo;
import io.agora.education.api.user.data.EduUserRole;
import io.agora.education.api.user.data.EduUserStateChangeType;
import io.agora.education.classroom.adapter.ClassVideoAdapter;
import io.agora.education.classroom.bean.board.BoardBean;
import io.agora.education.classroom.bean.board.BoardInfo;
import io.agora.education.classroom.bean.board.BoardState;
import io.agora.education.classroom.bean.channel.Room;
import io.agora.education.classroom.bean.msg.ChannelMsg;
import io.agora.education.classroom.bean.record.RecordBean;
import io.agora.education.classroom.bean.record.RecordMsg;
import io.agora.education.classroom.fragment.UserListFragment;
import io.agora.education.service.CommonService;
import io.agora.education.service.bean.ResponseBody;
import io.agora.education.service.bean.request.AllocateGroupReq;
import io.agora.education.service.bean.response.EduRoomInfoRes;

import static io.agora.education.EduApplication.getAppId;
import static io.agora.education.api.BuildConfig.API_BASE_URL;
import static io.agora.education.classroom.bean.board.BoardBean.BOARD;
import static io.agora.education.classroom.bean.record.RecordBean.RECORD;
import static io.agora.education.classroom.bean.record.RecordState.END;

public class BreakoutClassActivity extends BaseClassActivity implements TabLayout.OnTabSelectedListener {
    private static final String TAG = "BreakoutClassActivity";

    @BindView(R.id.layout_placeholder)
    protected ConstraintLayout layout_placeholder;
    @BindView(R.id.rcv_videos)
    protected RecyclerView rcv_videos;
    @BindView(R.id.layout_im)
    protected View layout_im;
    @BindView(R.id.layout_tab)
    protected TabLayout layout_tab;

    private ClassVideoAdapter classVideoAdapter;
    private UserListFragment userListFragment;
    private View teacherPlaceholderView;

    private EduRoom subEduRoom;

    @Override
    protected int getLayoutResId() {
        return R.layout.activity_breakout_class;
    }

    @Override
    protected void initData() {
        super.initData();
        /**needUserListener???false,????????????????????????????????????local??????*/
        joinRoom(getMainEduRoom(), roomEntry.getUserName(), roomEntry.getUserUuid(), true, false, true,
                new EduCallback<EduStudent>() {
                    @Override
                    public void onSuccess(@Nullable EduStudent res) {
                        joinSubEduRoom(getMainEduRoom(), roomEntry.getUserUuid(), roomEntry.getUserName());
                    }

                    @Override
                    public void onFailure(int code, @Nullable String reason) {
                        joinFailed(code, reason);
                    }
                });
        classVideoAdapter = new ClassVideoAdapter();
    }

    private void allocateGroup(String roomUuid, String userUuid, EduCallback<EduRoomInfo> callback) {
        AllocateGroupReq req = new AllocateGroupReq();
        RetrofitManager.instance().getService(API_BASE_URL, CommonService.class)
                .allocateGroup(getAppId(), roomUuid, req)
                .enqueue(new RetrofitManager.Callback<>(0, new ThrowableCallback<ResponseBody<EduRoomInfoRes>>() {
                    @Override
                    public void onFailure(@Nullable Throwable throwable) {
                        Log.e(TAG, "????????????????????????:" + throwable.getMessage());
                        getMainEduRoom().leave();
                        joinFailed(AgoraError.INTERNAL_ERROR.getValue(), throwable.getMessage());
                    }

                    @Override
                    public void onSuccess(@Nullable ResponseBody<EduRoomInfoRes> res) {
                        if (res != null && res.data != null) {
                            EduRoomInfo info = res.data;
                            callback.onSuccess(new EduRoomInfo(info.getRoomUuid(), info.getRoomName()));
                        }
                    }
                }));
    }

    /**
     * ???????????????????????????????????????????????????????????????
     *
     * @param mainRoom ?????????
     * @param userUuid ??????uuid
     */
    private void joinSubEduRoom(EduRoom mainRoom, String userUuid, String userName) {
        allocateGroup(mainRoom.getRoomInfo().getRoomUuid(), userUuid, new EduCallback<EduRoomInfo>() {
            @Override
            public void onSuccess(@Nullable EduRoomInfo res) {
                if (res != null) {
                    RoomCreateOptions createOptions = new RoomCreateOptions(res.getRoomUuid(),
                            res.getRoomName(), RoomType.BREAKOUT_CLASS.getValue());
                    subEduRoom = buildEduRoom(createOptions, mainRoom.getRoomInfo().getRoomUuid());
                    joinRoom(subEduRoom, userName, userUuid, true, true, true, new EduCallback<EduStudent>() {
                        @Override
                        public void onSuccess(@Nullable EduStudent res) {
                            /**???????????????userToken(???????????????user????????????room??????token?????????)*/
                            RetrofitManager.instance().addHeader("token",
                                    subEduRoom.getLocalUser().getUserInfo().getUserToken());
                            runOnUiThread(() -> showFragmentWithJoinSuccess());
                        }

                        @Override
                        public void onFailure(int code, @Nullable String reason) {
                            joinFailed(code, reason);
                        }
                    });
                }
            }

            @Override
            public void onFailure(int code, @Nullable String reason) {
                Log.e(TAG, "??????????????????->code:" + code + ", reason:" + reason);
            }
        });
    }

    @Override
    protected void initView() {
        super.initView();

        LinearLayoutManager layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false);
        rcv_videos.setLayoutManager(layoutManager);
        rcv_videos.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                super.getItemOffsets(outRect, view, parent, state);
                if (parent.getChildAdapterPosition(view) > 0) {
                    outRect.left = getResources().getDimensionPixelSize(R.dimen.dp_2_5);
                }
            }
        });
        rcv_videos.setAdapter(classVideoAdapter);
        layout_tab.addOnTabSelectedListener(this);
        userListFragment = new UserListFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.layout_chat_room, userListFragment)
                .show(userListFragment)
                .commitNow();
    }

    @Override
    protected int getClassType() {
        return Room.Type.BREAKOUT;
    }

    @Override
    public EduRoom getMyMediaRoom() {
        return subEduRoom;
    }

    @Override
    protected EduUserInfo getLocalUserInfo() {
//        return subEduRoom.getLocalUser().getUserInfo();
        return getMainEduRoom().getLocalUser().getUserInfo();
    }

    @Override
    public void sendRoomChatMsg(String msg, EduCallback<EduChatMsg> callback) {
        /**??????????????????roomUuid*/
        /**??????super????????????????????????????????????????????????fromRoomUuid???????????????-Web?????????*/
        EduRoomInfo subRoomInfo = subEduRoom.getRoomInfo();
        super.sendRoomChatMsg(new ChannelMsg.BreakoutChatMsgContent(EduUserRole.STUDENT.getValue(),
                msg, subRoomInfo.getRoomUuid(), subRoomInfo.getRoomName()).toJsonString(), callback);
        /**??????????????????????????????*/
        subEduRoom.getLocalUser().sendRoomChatMessage(new ChannelMsg.BreakoutChatMsgContent(
                EduUserRole.STUDENT.getValue(),
                msg, subRoomInfo.getRoomUuid(), subRoomInfo.getRoomName()).toJsonString(), callback);
    }

    @Override
    public void renderStream(EduRoom room, EduStreamInfo eduStreamInfo, @Nullable ViewGroup viewGroup) {
        /**????????????????????????:
         * TEACHER->mainEduRoom
         * STUDENT->subEduRoom*/
        EduBaseUserInfo publish = eduStreamInfo.getPublisher();
        if (publish.getRole().equals(EduUserRole.STUDENT)) {
            room = subEduRoom;
        }
        super.renderStream(room, eduStreamInfo, viewGroup);
    }

    /**
     * ?????????????????? ???????????? ??? ????????? ????????????????????????
     */
    private List<EduStreamInfo> getCurAllStudentStream() {
        return subEduRoom.getFullStreamList();
    }

    @Override
    protected List<EduUserInfo> getCurFullUser() {
        List<EduUserInfo> list = new ArrayList<>();
//        List<EduUserInfo> mainUsers = getMainEduRoom().getFullUserList();
        List<EduUserInfo> subUsers = subEduRoom.getFullUserList();
//        list.addAll(mainUsers);
        list.addAll(subUsers);
        return list;
    }

    @Override
    protected List<EduStreamInfo> getCurFullStream() {
        List<EduStreamInfo> list = new ArrayList<>();
        List<EduStreamInfo> mainStreams = getMainEduRoom().getFullStreamList();
        List<EduStreamInfo> subStreams = new ArrayList<>();
        if (subEduRoom != null) {
            subStreams = subEduRoom.getFullStreamList();
        }
        list.addAll(mainStreams);
        list.addAll(subStreams);
        return list;
    }

    private void showVideoList(List<EduStreamInfo> list) {
        runOnUiThread(() -> {
            for (int i = 0; i < list.size(); i++) {
                EduStreamInfo streamInfo = list.get(i);
                if (streamInfo.getPublisher().getRole().equals(EduUserRole.TEACHER)) {
                    /*???????????????????????????*/
                    layout_placeholder.setVisibility(View.GONE);
                    if (i != 0) {
                        Collections.swap(list, 0, i);
                    }
                    classVideoAdapter.setNewList(list);
                    return;
                }
            }
            /*???????????????????????????*/
            if (teacherPlaceholderView == null) {
                teacherPlaceholderView = LayoutInflater.from(this).inflate(R.layout.layout_video_small_class,
                        layout_placeholder);
            }
            layout_placeholder.setVisibility(View.VISIBLE);
            classVideoAdapter.setNewList(list);
        });
    }

    @OnClick(R.id.iv_float)
    public void onClick(View view) {
        boolean isSelected = view.isSelected();
        view.setSelected(!isSelected);
        layout_im.setVisibility(isSelected ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        if (getMyMediaRoom() != null) {
            getMyMediaRoom().leave();
            subEduRoom = null;
        }
        super.onDestroy();
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (tab.getPosition() == 0) {
            transaction.show(chatRoomFragment).hide(userListFragment);
        } else {
            transaction.show(userListFragment).hide(chatRoomFragment);
        }
        transaction.commitNow();
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {

    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {

    }


    @Override
    public void onRemoteUsersInitialized(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        if (classRoom.equals(subEduRoom)) {
            /**?????????????????????roomProperties??????????????????????????????????????????????????????,??????RTM??????*/
            if (mainBoardBean == null) {
                Log.e(TAG, "??????????????????????????????");
                requestBoardInfo((getMainEduRoom().getLocalUser().getUserInfo()).getUserToken(),
                        getAppId(), classRoom.getRoomInfo().getRoomUuid());
            } else {
                BoardInfo info = mainBoardBean.getInfo();
                BoardState state = mainBoardBean.getState();
                runOnUiThread(() -> {
                    whiteboardFragment.initBoardWithRoomToken(info.getBoardId(),
                            info.getBoardToken(), getLocalUserInfo().getUserUuid());
//                    boolean follow = whiteBoardIsFollowMode(state);
//                    whiteboardFragment.disableCameraTransform(follow);
//                    boolean granted = whiteBoardIsGranted((state));
//                    whiteboardFragment.disableDeviceInputs(!granted);
//                    if (follow) {
//                        layout_whiteboard.setVisibility(View.VISIBLE);
//                        layout_share_video.setVisibility(View.GONE);
//                    }
                });
            }
            title_view.setTitle(String.format(Locale.getDefault(), "%s", getMediaRoomName()));
        } else {
            EduRoomStatus roomStatus = getMainEduRoom().getRoomStatus();
            title_view.setTimeState(roomStatus.getCourseState() == EduRoomState.START,
                    System.currentTimeMillis() - roomStatus.getStartTime());
            chatRoomFragment.setMuteAll(!roomStatus.isStudentChatAllowed());
            /**??????roomProperties*/
            Map<String, Object> roomProperties = classRoom.getRoomProperties();
            String boardJson = getProperty(roomProperties, BOARD);
            if (!TextUtils.isEmpty(boardJson)) {
                Log.e(TAG, "?????????????????????????????????->" + boardJson);
                mainBoardBean = new Gson().fromJson(boardJson, BoardBean.class);
            }
        }
    }

    @Override
    public void onRemoteUsersJoined(@NotNull List<? extends EduUserInfo> users, @NotNull EduRoom classRoom) {
        super.onRemoteUsersJoined(users, classRoom);
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setUserList(getCurAllStudentStream());
            title_view.setTitle(String.format(Locale.getDefault(), "%s", getMediaRoomName()));
        }
    }

    @Override
    public void onRemoteUserLeft(@NotNull EduUserEvent userEvent, @NotNull EduRoom classRoom) {
        super.onRemoteUserLeft(userEvent, classRoom);
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setUserList(getCurAllStudentStream());
            title_view.setTitle(String.format(Locale.getDefault(), "%s", getMediaRoomName()));
        }
    }

    @Override
    public void onRemoteUserUpdated(@NotNull EduUserEvent userEvent, @NotNull EduUserStateChangeType type,
                                    @NotNull EduRoom classRoom) {
        super.onRemoteUserUpdated(userEvent, type, classRoom);
        if (classRoom.equals(subEduRoom)) {
        }
    }

    @Override
    public void onRoomMessageReceived(@NotNull EduMsg message, @NotNull EduRoom classRoom) {
        super.onRoomMessageReceived(message, classRoom);
        if (classRoom.equals(subEduRoom)) {
        }
    }

    @Override
    public void onUserMessageReceived(@NotNull EduMsg message) {
        super.onUserMessageReceived(message);
    }

    @Override
    public void onRoomChatMessageReceived(@NotNull EduChatMsg eduChatMsg, @NotNull EduRoom classRoom) {
        /**??????????????????????????????????????????*/
        EduUserInfo fromUser = eduChatMsg.getFromUser();
        ChannelMsg.ChatMsg chatMsg = new ChannelMsg.ChatMsg(fromUser, eduChatMsg.getMessage(),
                eduChatMsg.getType(), true, getRoleStr(fromUser.getRole().getValue()));
        chatMsg.isMe = fromUser.equals(classRoom.getLocalUser().getUserInfo());
        ChannelMsg.BreakoutChatMsgContent msgContent = new Gson().fromJson(chatMsg.getMessage(),
                ChannelMsg.BreakoutChatMsgContent.class);
        chatMsg.setMessage(msgContent.getContent());
        boolean rev = false;
        if (classRoom.equals(getMainEduRoom())) {
            /**????????????????????????????????????????????????????????????????????????????????????????????????*/
            if (fromUser.getRole().equals(EduUserRole.TEACHER) || msgContent.getFromRoomUuid().equals(
                    subEduRoom.getRoomInfo().getRoomUuid())) {
                rev = true;
            }
        } else if (classRoom.equals(subEduRoom)) {
            /**????????????????????????????????????????????????*/
            rev = true;
        }
        boolean isTeacherMsgToMain = classRoom.equals(getMainEduRoom()) && fromUser.getRole()
                .equals(EduUserRole.TEACHER) && TextUtils.isEmpty(msgContent.getFromRoomUuid());
        boolean isTeacherMsgToSub = classRoom.equals(getMainEduRoom()) && fromUser.getRole()
                .equals(EduUserRole.TEACHER) && msgContent.getFromRoomUuid().equals(subEduRoom
                .getRoomInfo().getRoomUuid());
        boolean isGroupMsg = classRoom.equals(subEduRoom);
        if (isTeacherMsgToMain || isTeacherMsgToSub || isGroupMsg) {
            chatRoomFragment.addMessage(chatMsg);
            Log.e(TAG, "??????????????????????????????");
        }
    }

    @Override
    public void onUserChatMessageReceived(@NotNull EduChatMsg chatMsg) {
        super.onUserChatMessageReceived(chatMsg);
//        if (classRoom.equals(subEduRoom)) {
//        }
    }

    @Override
    public void onRemoteStreamsInitialized(@NotNull List<? extends
            EduStreamInfo> streams, @NotNull EduRoom classRoom) {
        super.onRemoteStreamsInitialized(streams, classRoom);
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setLocalUserUuid(classRoom.getLocalUser().getUserInfo().getUserUuid());
            userListFragment.setUserList(getCurAllStudentStream());
            showVideoList(getCurFullStream());
        } else {
            boolean notify = false;
            for (EduStreamInfo streamInfo : streams) {
                EduBaseUserInfo publisher = streamInfo.getPublisher();
                if (publisher.getRole().equals(EduUserRole.TEACHER)) {
                    switch (streamInfo.getVideoSourceType()) {
                        case CAMERA:
                            notify = true;
                            break;
                        case SCREEN:
                            /*????????????????????????????????????????????????????????????*/
                            runOnUiThread(() -> {
                                layout_whiteboard.setVisibility(View.GONE);
                                layout_share_video.setVisibility(View.VISIBLE);
                                layout_share_video.removeAllViews();
                                renderStream(getMainEduRoom(), streamInfo, layout_share_video);
                            });
                            break;
                        default:
                            break;
                    }
                }
            }
            if (notify) {
                /*??????????????????????????????????????????????????????????????????????????????*/
                List<EduStreamInfo> list = getMainEduRoom().getFullStreamList();
                showVideoList(list);
            }
        }
    }

    @Override
    public void onRemoteStreamsAdded
            (@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        /**???????????????????????????super???????????????*/
        super.onRemoteStreamsAdded(streamEvents, classRoom);
        /**??????????????????*/
        boolean notify = false;
        for (EduStreamEvent streamEvent : streamEvents) {
            EduStreamInfo streamInfo = streamEvent.getModifiedStream();
            switch (streamInfo.getVideoSourceType()) {
                case CAMERA:
                    notify = true;
                    break;
                default:
                    break;
            }
        }
        /**?????????Camera??????????????????????????????*/
        if (notify) {
            Log.e(TAG, "?????????Camera??????????????????????????????");
            showVideoList(getCurFullStream());
        }
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setUserList(getCurAllStudentStream());
        }
    }

    @Override
    public void onRemoteStreamUpdated(@NotNull EduStreamEvent
                                              streamEvent, @NotNull EduStreamStateChangeType type,
                                      @NotNull EduRoom classRoom) {
        /**???????????????????????????super???????????????*/
        super.onRemoteStreamUpdated(streamEvent, type, classRoom);
        /**??????????????????*/
        boolean notify = false;
        EduStreamInfo streamInfo = streamEvent.getModifiedStream();
        switch (streamInfo.getVideoSourceType()) {
            case CAMERA:
                notify = true;
                break;
            default:
                break;
        }
        /**?????????Camera??????????????????????????????*/
        if (notify) {
            Log.e(TAG, "?????????Camera?????????????????????????????????");
            showVideoList(getCurFullStream());
        }
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setUserList(getCurAllStudentStream());
        }
    }

    @Override
    public void onRemoteStreamsRemoved
            (@NotNull List<EduStreamEvent> streamEvents, @NotNull EduRoom classRoom) {
        /**???????????????????????????super???????????????*/
        super.onRemoteStreamsRemoved(streamEvents, classRoom);
        /**??????????????????*/
        boolean notify = false;
        for (EduStreamEvent streamEvent : streamEvents) {
            EduStreamInfo streamInfo = streamEvent.getModifiedStream();
            switch (streamInfo.getVideoSourceType()) {
                case CAMERA:
                    notify = true;
                    break;
                default:
                    break;
            }
        }
        /**?????????Camera?????????????????????????????????*/
        if (notify) {
            Log.e(TAG, "?????????Camera?????????????????????????????????");
            showVideoList(getCurFullStream());
        }
        if (classRoom.equals(subEduRoom)) {
            userListFragment.setUserList(getCurAllStudentStream());
        }
    }

    @Override
    public void onRoomStatusChanged(@NotNull EduRoomChangeType event, @NotNull EduUserInfo
            operatorUser, @NotNull EduRoom classRoom) {
        /**?????????????????????super??????*/
        if (classRoom.equals(getMainEduRoom())) {
            EduRoomStatus roomStatus = classRoom.getRoomStatus();
            switch (event) {
                case CourseState:
                    Log.e(TAG, "??????:" + getMainEduRoom().getRoomInfo().getRoomUuid() + "??????????????????->"
                            + roomStatus.getCourseState());
                    title_view.setTimeState(roomStatus.getCourseState() == EduRoomState.START,
                            System.currentTimeMillis() - roomStatus.getStartTime());
                    break;
                case AllStudentsChat:
                    chatRoomFragment.setMuteAll(!roomStatus.isStudentChatAllowed());
                    break;
                default:
                    break;
            }
        }
    }

    @Override
    public void onRoomPropertyChanged(@NotNull EduRoom
                                              classRoom, @Nullable Map<String, Object> cause) {
        if (!classRoom.equals(subEduRoom)) {
            Log.e(TAG, "??????????????????roomProperty???????????????");
            Map<String, Object> roomProperties = classRoom.getRoomProperties();
            String boardJson = getProperty(roomProperties, BOARD);
            if (!TextUtils.isEmpty(boardJson) && mainBoardBean == null) {
                Log.e(TAG, "???????????????????????????????????????->" + boardJson);
                /**???????????????????????????*/
                mainBoardBean = new Gson().fromJson(boardJson, BoardBean.class);
                runOnUiThread(() -> {
                    whiteboardFragment.initBoardWithRoomToken(mainBoardBean.getInfo().getBoardId(),
                            mainBoardBean.getInfo().getBoardToken(), getLocalUserInfo().getUserUuid());
//                    boolean follow = whiteBoardIsFollowMode(mainBoardBean.getState());
//                    whiteboardFragment.disableCameraTransform(follow);
//                    boolean granted = whiteBoardIsGranted((mainBoardBean.getState()));
//                    whiteboardFragment.disableDeviceInputs(!granted);
//                    if (follow) {
//                        layout_whiteboard.setVisibility(View.VISIBLE);
//                        layout_share_video.setVisibility(View.GONE);
//                    }
                });
            }
            String recordJson = getProperty(roomProperties, RECORD);
            if (!TextUtils.isEmpty(recordJson)) {
                RecordBean tmp = RecordBean.fromJson(recordJson, RecordBean.class);
                if (mainRecordBean == null || tmp.getState() != mainRecordBean.getState()) {
                    mainRecordBean = tmp;
                    if (mainRecordBean.getState() == END) {
                        RecordMsg recordMsg = new RecordMsg(getMainEduRoom().getRoomInfo().getRoomUuid(),
                                getLocalUserInfo(), getString(R.string.replay_link),
                                EduChatMsgType.Text.getValue());
                        recordMsg.isMe = true;
                        chatRoomFragment.addMessage(recordMsg);
                    }
                }
            }
        }
    }

    @Override
    public void onRemoteUserPropertyUpdated(@NotNull EduUserInfo userInfo, @NotNull EduRoom
            classRoom, @Nullable Map<String, Object> cause) {
        super.onRemoteUserPropertyUpdated(userInfo, classRoom, cause);
        if (classRoom.equals(subEduRoom)) {
        }
    }

    @Override
    public void onNetworkQualityChanged(@NotNull NetworkQuality quality, @NotNull EduUserInfo
            user, @NotNull EduRoom classRoom) {
        super.onNetworkQualityChanged(quality, user, classRoom);
        if (classRoom.equals(subEduRoom)) {
            title_view.setNetworkQuality(quality);
        }
    }

    @Override
    public void onConnectionStateChanged(@NotNull ConnectionState state, @NotNull EduRoom
            classRoom) {
        super.onConnectionStateChanged(state, classRoom);
    }

    @Override
    public void onLocalUserUpdated(@NotNull EduUserEvent
                                           userEvent, @NotNull EduUserStateChangeType type) {
        super.onLocalUserUpdated(userEvent, type);
        /**??????????????????*/
        showVideoList(getCurFullStream());
        userListFragment.updateLocalStream(getLocalCameraStream());
    }

    @Override
    public void onLocalUserPropertyUpdated(@NotNull EduUserInfo
                                                   userInfo, @Nullable Map<String, Object> cause) {
        super.onLocalUserPropertyUpdated(userInfo, cause);
    }

    @Override
    public void onLocalStreamAdded(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamAdded(streamEvent);
        showVideoList(getCurFullStream());
        userListFragment.updateLocalStream(getLocalCameraStream());
    }

    @Override
    public void onLocalStreamUpdated(@NotNull EduStreamEvent
                                             streamEvent, @NotNull EduStreamStateChangeType type) {
        super.onLocalStreamUpdated(streamEvent, type);
        showVideoList(getCurFullStream());
        userListFragment.updateLocalStream(getLocalCameraStream());
    }

    @Override
    public void onLocalStreamRemoved(@NotNull EduStreamEvent streamEvent) {
        super.onLocalStreamRemoved(streamEvent);
        /**???????????????????????????classroom??????????????????????????????????????????????????????*/
        Log.e(TAG, "??????????????????:" + streamEvent.getModifiedStream().getStreamUuid());
    }

    @Override
    public void onGlobalStateChanged(GlobalState state) {
        super.onGlobalStateChanged(state);
    }
}
