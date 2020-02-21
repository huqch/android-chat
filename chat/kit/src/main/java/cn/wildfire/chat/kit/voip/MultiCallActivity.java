package cn.wildfire.chat.kit.voip;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProviders;

import org.webrtc.StatsReport;

import java.util.ArrayList;
import java.util.List;

import cn.wildfire.chat.kit.group.GroupViewModel;
import cn.wildfire.chat.kit.group.PickGroupMemberActivity;
import cn.wildfirechat.avenginekit.AVEngineKit;
import cn.wildfirechat.model.GroupInfo;
import cn.wildfirechat.remote.ChatManager;

public class MultiCallActivity extends VoipBaseActivity {

    private static final int REQUEST_CODE_ADD_PARTICIPANT = 100;
    private String groupId;
    private AVEngineKit.CallSessionCallback currentCallSessionCallback;

    private boolean isAddParticipant = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    public void setAddParticipant(boolean addParticipant) {
        isAddParticipant = addParticipant;
    }

    @Override
    protected void onStop() {
        super.onStop();
        AVEngineKit.CallSession session = gEngineKit.getCurrentSession();
        if (session != null && session.getState() != AVEngineKit.CallState.Idle && !isAddParticipant) {
            showFloatingView();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideFloatingView();
    }

    private void init() {
        AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
        if (session == null) {
            finish();
            return;
        }
        session.setCallback(this);
        groupId = session.getConversation().target;

        Fragment fragment;
        if (session.isAudioOnly()) {
            fragment = new MultiCallAudioFragment();
        } else {
            fragment = new MultiCallVideoFragment();
        }

        currentCallSessionCallback = (AVEngineKit.CallSessionCallback) fragment;
        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction()
                .add(android.R.id.content, fragment)
                .commit();
    }


    // hangup 也会触发
    @Override
    public void didCallEndWithReason(AVEngineKit.CallEndReason callEndReason) {
        postAction(() -> {
            if (!isFinishing()) {
                finish();
            }
        });
    }

    // 自己的状态
    @Override
    public void didChangeState(AVEngineKit.CallState callState) {
        postAction(() -> {
            currentCallSessionCallback.didChangeState(callState);
        });
    }

    @Override
    public void didParticipantJoined(String userId) {
        postAction(() -> {
            currentCallSessionCallback.didParticipantJoined(userId);
        });
    }

    @Override
    public void didParticipantLeft(String userId, AVEngineKit.CallEndReason callEndReason) {
        postAction(() -> {
            currentCallSessionCallback.didParticipantLeft(userId, callEndReason);
        });
    }

    @Override
    public void didChangeMode(boolean audioOnly) {
        postAction(() -> {
            if (audioOnly) {
                Fragment fragment = new MultiCallAudioFragment();
                currentCallSessionCallback = (AVEngineKit.CallSessionCallback) fragment;
                FragmentManager fragmentManager = getSupportFragmentManager();
                fragmentManager.beginTransaction()
                        .replace(android.R.id.content, fragment)
                        .commit();
            } else {
                // never called
            }
        });
    }

    @Override
    public void didCreateLocalVideoTrack() {
        postAction(() -> {
            currentCallSessionCallback.didCreateLocalVideoTrack();
        });
    }

    @Override
    public void didReceiveRemoteVideoTrack(String userId) {
        postAction(() -> {
            currentCallSessionCallback.didReceiveRemoteVideoTrack(userId);
        });
    }

    @Override
    public void didRemoveRemoteVideoTrack(String userId) {
        postAction(() -> {
            currentCallSessionCallback.didRemoveRemoteVideoTrack(userId);
        });
    }

    @Override
    public void didError(String reason) {
        postAction(() -> {
            currentCallSessionCallback.didError(reason);
        });
    }

    @Override
    public void didGetStats(StatsReport[] statsReports) {
        postAction(() -> {
            currentCallSessionCallback.didGetStats(statsReports);
        });
    }

    @Override
    public void didVideoMuted(String s, boolean b) {
        postAction(() -> currentCallSessionCallback.didVideoMuted(s, b));
    }

    public void showFloatingView() {
        if (!checkOverlayPermission()) {
            return;
        }

        Intent intent = new Intent(this, MultiCallFloatingService.class);
        startService(intent);
        finish();
    }

    public void hideFloatingView() {
        Intent intent = new Intent(this, MultiCallFloatingService.class);
        stopService(intent);
    }

    void addParticipant() {
        isAddParticipant = true;
        Intent intent = new Intent(this, PickGroupMemberActivity.class);
        GroupViewModel groupViewModel = ViewModelProviders.of(this).get(GroupViewModel.class);
        GroupInfo groupInfo = groupViewModel.getGroupInfo(groupId, false);
        intent.putExtra(PickGroupMemberActivity.GROUP_INFO, groupInfo);
        List<String> participants = getEngineKit().getCurrentSession().getParticipantIds();
        participants.add(ChatManager.Instance().getUserId());
        intent.putStringArrayListExtra(PickGroupMemberActivity.CHECKED_MEMBER_IDS, (ArrayList<String>) participants);
        intent.putStringArrayListExtra(PickGroupMemberActivity.UNCHECKABLE_MEMBER_IDS, (ArrayList<String>) participants);
        intent.putExtra(PickGroupMemberActivity.MAX_COUNT, 9);
        startActivityForResult(intent, REQUEST_CODE_ADD_PARTICIPANT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ADD_PARTICIPANT) {
            isAddParticipant = false;
            if (resultCode == RESULT_OK) {
                List<String> newParticipants = data.getStringArrayListExtra(PickGroupMemberActivity.EXTRA_RESULT);
                if (newParticipants != null && !newParticipants.isEmpty()) {
                    AVEngineKit.CallSession session = getEngineKit().getCurrentSession();
                    session.inviteNewParticipants(newParticipants);
                }
            }
        }
    }
}
