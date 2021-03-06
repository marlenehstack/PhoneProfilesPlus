package sk.henrichg.phoneprofilesplus;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.SystemClock;

public class PhoneCallBroadcastReceiver extends PhoneCallReceiver {

    private static AudioManager audioManager = null;

    private static boolean savedSpeakerphone = false;
    private static boolean speakerphoneSelected = false;

    //static boolean linkUnlinkExecuted = false;
    //static boolean speakerphoneOnExecuted = false;

    private static final int SERVICE_PHONE_EVENT_START = 1;
    private static final int SERVICE_PHONE_EVENT_ANSWER = 2;
    private static final int SERVICE_PHONE_EVENT_END = 3;

    static final int LINKMODE_NONE = 0;
    static final int LINKMODE_LINK = 1;
    private static final int LINKMODE_UNLINK = 2;

    protected boolean onStartReceive()
    {
        PPApplication.logE("##### PhoneCallBroadcastReceiver.onReceive", "xxx");

        return PPApplication.getApplicationStarted(savedContext, true);
    }

    protected void onEndReceive()
    {
    }

    protected void onIncomingCallStarted(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_START, true, false/*, number, eventTime*/);
    }

    //protected void onOutgoingCallStarted(/*String number, Date eventTime*/)
    //{
    //    doCall(savedContext, SERVICE_PHONE_EVENT_START, false, false/*, number, eventTime*/);
    //}

    protected void onIncomingCallAnswered(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_ANSWER, true, false/*, number, eventTime*/);
    }

    protected void onOutgoingCallAnswered(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_ANSWER, false, false/*, number, eventTime*/);
    }

    protected void onIncomingCallEnded(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_END, true, false/*, number, eventTime*/);
    }

    protected void onOutgoingCallEnded(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_END, false, false/*, number, eventTime*/);
    }

    protected void onMissedCall(/*String number, Date eventTime*/)
    {
        doCall(savedContext, SERVICE_PHONE_EVENT_END, true, true/*, number, eventTime*/);
    }

    private void doCall(final Context context, final int phoneEvent,
                            final boolean incoming, final boolean missed/*,
                            final String number, final Date eventTime*/) {
        final Context appContext = context.getApplicationContext();
        PPApplication.startHandlerThread("PhoneCallBroadcastReceiver.doCall");
        final Handler handler = new Handler(PPApplication.handlerThread.getLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                switch (phoneEvent) {
                    case SERVICE_PHONE_EVENT_START:
                        callStarted(incoming, /*number, eventTime,*/ appContext);
                        break;
                    case SERVICE_PHONE_EVENT_ANSWER:
                        callAnswered(incoming, /*number, eventTime,*/ appContext);
                        break;
                    case SERVICE_PHONE_EVENT_END:
                        callEnded(incoming, missed, /*number, eventTime,*/ appContext);
                        break;
                }
            }
        });
    }

    private static void setLinkUnlinkNotificationVolume(final int linkMode, final Context context) {
        if (!RingerModeChangeReceiver.notUnlinkVolumes) {
            if (ActivateProfileHelper.getMergedRingNotificationVolumes(context) && ApplicationPreferences.applicationUnlinkRingerNotificationVolumes(context)) {
                if (ActivateProfileHelper.isAudibleSystemRingerMode(audioManager, context)) {
                    DataWrapper dataWrapper = new DataWrapper(context, false, 0, false);
                    final Profile profile = dataWrapper.getActivatedProfile(false, false);
                    if (profile != null) {
                        ActivateProfileHelper.executeForVolumes(profile, linkMode, false, context);
                    }
                    dataWrapper.invalidateDataWrapper();
                }
            }
        }
    }

    private static void callStarted(boolean incoming, /*String phoneNumber, Date eventTime,*/ Context context)
    {
        if (audioManager == null )
            audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        PPApplication.logE("PhoneCallBroadcastReceiver.callStarted", "incoming="+incoming);
        //PPApplication.logE("PhoneCallBroadcastReceiver.callStarted", "phoneNumber="+phoneNumber);

        if (incoming) {
            setLinkUnlinkNotificationVolume(LINKMODE_UNLINK, context);
        }
    }

    private static void callAnswered(boolean incoming, /*String phoneNumber, Date eventTime,*/ Context context)
    {
        speakerphoneSelected = false;

        if (audioManager == null )
            audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        PPApplication.logE("PhoneCallBroadcastReceiver.callAnswered", "incoming="+incoming);

        if (PhoneProfilesService.getInstance() != null)
            PhoneProfilesService.getInstance().stopSimulatingRingingCall();

        // Delay 2 seconds mode changed to MODE_IN_CALL
        long start = SystemClock.uptimeMillis();
        do {
            if (audioManager.getMode() != AudioManager.MODE_IN_CALL)
                //try { Thread.sleep(100); } catch (InterruptedException e) {};
                SystemClock.sleep(100);
            else
                break;
        } while (SystemClock.uptimeMillis() - start < 2000);

        // audio mode is set to MODE_IN_CALL by system

        DataWrapper dataWrapper = new DataWrapper(context, false, 0, false);

        Profile profile = dataWrapper.getActivatedProfile(false, false);
        profile = Profile.getMappedProfile(profile, context);

        if (profile != null) {
            if (profile._volumeSpeakerPhone != 0) {
                savedSpeakerphone = audioManager.isSpeakerphoneOn();
                boolean changeSpeakerphone = false;
                if (savedSpeakerphone && (profile._volumeSpeakerPhone == 2)) // 2=speakerphone off
                    changeSpeakerphone = true;
                if ((!savedSpeakerphone) && (profile._volumeSpeakerPhone == 1)) // 1=speakerphone on
                    changeSpeakerphone = true;
                if (changeSpeakerphone) {
                    /// activate SpeakerPhone
                    audioManager.setSpeakerphoneOn(profile._volumeSpeakerPhone == 1);
                    speakerphoneSelected = true;
                }
            }
        }

        dataWrapper.invalidateDataWrapper();

        // setSpeakerphoneOn() moved to ActivateProfileHelper.executeForVolumes
    }

    private static void callEnded(boolean incoming, boolean missed, /*String phoneNumber, Date eventTime,*/ Context context)
    {
        if (audioManager == null )
            audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        PPApplication.logE("PhoneCallBroadcastReceiver.callEnded", "incoming="+incoming);
        PPApplication.logE("PhoneCallBroadcastReceiver.callEnded", "missed="+missed);

        if (PhoneProfilesService.getInstance() != null)
            PhoneProfilesService.getInstance().stopSimulatingRingingCall();

        // audio mode is set to MODE_IN_CALL by system

        if (speakerphoneSelected)
        {
            if (audioManager != null)
                audioManager.setSpeakerphoneOn(savedSpeakerphone);
        }

        speakerphoneSelected = false;

        // Delay 2 seconds mode changed to MODE_NORMAL
        long start = SystemClock.uptimeMillis();
        do {
            if (audioManager.getMode() != AudioManager.MODE_NORMAL)
                //try { Thread.sleep(100); } catch (InterruptedException e) {};
                SystemClock.sleep(100);
            else
                break;
        } while (SystemClock.uptimeMillis() - start < 2000);

        // audio mode is set to MODE_NORMAL by system

        if (incoming)
            setLinkUnlinkNotificationVolume(LINKMODE_LINK, context);
    }

}
