package ru.simdev.livetex;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

import org.apache.thrift.TException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import livetex.capabilities.Capabilities;
import livetex.dialog.DialogAttributes;
import livetex.queue_service.Destination;
import livetex.queue_service.SendMessageResponse;
import ru.simdev.livetex.fragments.presenters.InitPresenter;
import ru.simdev.livetex.models.BaseMessage;
import ru.simdev.livetex.models.ErrorMessage1;
import ru.simdev.livetex.models.EventMessage;
import ru.simdev.livetex.utils.BusProvider;
import ru.simdev.livetex.utils.ThreadUtils;
import sdk.Livetex;
import sdk.data.DataKeeper;
import sdk.handler.AHandler;
import sdk.handler.IInitHandler;
import sdk.handler.INotificationDialogHandler;
import sdk.models.LTDialogAttributes;
import sdk.models.LTDialogState;
import sdk.models.LTEmployee;
import sdk.models.LTFileMessage;
import sdk.models.LTHoldMessage;
import sdk.models.LTSerializableHolder;
import sdk.models.LTTextMessage;
import sdk.models.LTTypingMessage;
import sdk.utils.FileUtils;

public class LivetexContext {

    private static final String TAG = "Livetex";

    private static LivetexContext sInstance = null;

    private static Boolean inited = false;

    private Context mContext;

    public static String currentConversation = "";
    public static boolean IS_ACTIVE = false;
    private static List<Activity> externalActivitiesStack = new ArrayList<>();

    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private static final String AUTH_URL_REAL = "https://authentication-service-sdk-production-1.livetex.ru";
    private static final String API_KEY_REAL = "sdkkey235860-163721";

    private static String AUTH_URL = AUTH_URL_REAL;
    private static String API_KEY = API_KEY_REAL;

    public static void setProductionScope() {
        API_KEY = API_KEY_REAL;
        AUTH_URL = AUTH_URL_REAL;
    }

    private static Livetex sLiveTex;

    private InitPresenter presenter;

    public static Livetex getsLiveTex() {
        return sLiveTex;
    }

    public LivetexContext(Context context) {
        mContext = context;
        sInstance = this;

        init();
    }

    public static boolean isReady() {
        return sInstance != null;
    }

    public static LivetexContext instance() {
        return sInstance;
    }

    public void updateContext(Context context) {
        mContext = context;
    }

    public static void dispatchOnUIThread(Runnable r) {
        sHandler.post(r);
    }

    public static void dispatchOnUIThreadAfter(Runnable r, long after) {
        sHandler.postDelayed(r, after);
    }

    public void init() {
        boolean isMainProcess = ThreadUtils.getNameFromActivityThread().equals(mContext.getPackageName());

        if (isMainProcess && !inited) {
            DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .build();

            ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(mContext.getApplicationContext())
                    .defaultDisplayImageOptions(defaultOptions)
                    .build();

            ImageLoader.getInstance().init(config); // Do it on Application start

            LivetexContext.setProductionScope();

            dispatchOnUIThread(new Runnable() {
                @Override
                public void run() {
                    BusProvider.register(this);
                }
            });

            inited = true;
        } else {
            // init here additional stuff like remote services (adverts, metrics etc.)
        }
    }

    public static void pushToActivitiesStack(Activity activity) {
        externalActivitiesStack.add(activity);
    }

    public static void clearExternalActivitiesStack() {
        for (Activity activity : externalActivitiesStack) {
            activity.finish();
        }
    }

    public void initPresenter() {
        Log.w(TAG, "run init presenter");
        presenter = new InitPresenter();
        presenter.init(mContext, Const.APP_ID);
    }

    public static void initLivetex(String id, String regId) {
        initLivetex(id, regId, null);

    }

    public static void initLivetex(String id, String regId, final AHandler<Boolean> handler) {
        ArrayList<Capabilities> capabilities = new ArrayList(){{add(Capabilities.QUEUE);}};

        sLiveTex = new Livetex.Builder(instance().mContext, API_KEY, id)
                .addAuthUrl(AUTH_URL)
                .addDeviceId(regId)
                .addCapabilities(capabilities)
                .addToken(DataKeeper.restoreToken(instance().mContext))
                .build();

        sLiveTex.init(new IInitHandler() {
            @Override
            public void onSuccess(String token) {
                Log.w(TAG, "LIVETEX INITED " + token);
                sdk.data.DataKeeper.saveToken(instance().mContext, token);
                postMessage(new EventMessage(BaseMessage.TYPE.INIT, token));
                if (handler != null) {
                    handler.onResultRecieved(true);
                }

                sLiveTex.setNotificationDialogHandler(new INotificationDialogHandler() {
                    @Override
                    public void updateDialogState(LTDialogState state) throws TException {
                        if (state.getEmployee() == null) {
                            EventMessage eventMessage = new EventMessage(BaseMessage.TYPE.CLOSE);
                            postMessage(eventMessage);
                        } else {
                            EventMessage eventMessage = new EventMessage(BaseMessage.TYPE.UPDATE_STATE);
                            eventMessage.putSerializable(state.getEmployee());
                            postMessage(eventMessage);
                        }
                    }

                    @Override
                    public void receiveHoldMessage(LTHoldMessage message) throws TException {
                        Log.w(TAG, "notify receiveHoldMessage: " + message);
                    }

                    @Override
                    public void receiveTypingMessage(LTTypingMessage message) throws TException {
                        EventMessage eventMessage = new EventMessage(BaseMessage.TYPE.TYPING_MESSAGE);
                        postMessage(eventMessage);
                    }

                    @Override
                    public void onError(String message) {
                        Log.w(TAG, "notif onError: " + message);
                    }

                    @Override
                    public void selectDestination(List<livetex.visitor_notification.Destination> destinations) throws TException {
                        Log.v(TAG, "selectDestination: " + destinations.size());
                    }

                    @Override
                    public void receiveTextMessage(LTTextMessage message) throws TException {
                        Log.w(TAG, "receiveTextMessage");
                        EventMessage eventMessage = new EventMessage(BaseMessage.TYPE.RECEIVE_QUEUE_MSG);
                        eventMessage.putSerializable(message);
                        postMessage(eventMessage);
                    }

                    @Override
                    public void receiveFileMessage(LTFileMessage message) throws TException {
                        EventMessage eventMessage = new EventMessage(BaseMessage.TYPE.RECEIVE_FILE);
                        eventMessage.putSerializable(message);
                        postMessage(eventMessage);
                    }

                    @Override
                    public void confirm(String messageId) throws TException {

                    }
                });
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "init error " + errorMessage);
                postMessage(new ErrorMessage1(BaseMessage.TYPE.INIT, errorMessage));
            }
        });

    }

    public static void postMessage(BaseMessage message) {
        Log.e(TAG, "postMessage" + message.getStringExtra());
        if (message != null) {
            dispatchOnUIThread(new Runnable() {
                @Override
                public void run() {
                    BusProvider.getInstance().post(message);
                }
            });
        }
    }


    public static void confirmQueueMessage(String messageId) {
        if (sLiveTex != null) {
            sLiveTex.confirm(messageId);
        }
    }

    public static void getLastMessages(long offset, long limit, AHandler<LTSerializableHolder> handler) {
        if (sLiveTex != null) {
            sLiveTex.getLastMessages(offset, limit, handler);
        }
    }

    public static void requestDialogByEmployee(String id, String livetexId, AHandler<LTDialogState> handler) {
        if (sLiveTex != null) {
            DialogAttributes dialogAttributes = new DialogAttributes();
            HashMap<String, String> hashMap = new HashMap<>();
            hashMap.put("Livetex ID", livetexId);
            dialogAttributes.setVisible(hashMap);
            LTEmployee operator = new LTEmployee(id, "online", "", "", "");
        }
    }

    public static void getDestinations(AHandler<ArrayList<Destination>> handler) {
        if (sLiveTex != null) {
            Log.w(TAG, "getDestinations");
            sLiveTex.getDestinations(handler);
        }
    }

    public static void setDestination(Destination destination, LTDialogAttributes dialogAttrs) {

        if(sLiveTex != null) {
            sLiveTex.setDestination(destination, dialogAttrs);
        }
    }

    public static void sendTextMessage(String message, AHandler<SendMessageResponse> handler) {
        if(sLiveTex != null) {
            sLiveTex.sendTextMessage(message, handler);
        }
    }

    public static void getStateQueue(AHandler<livetex.queue_service.DialogState> handler) {
        if(sLiveTex != null) {
            sLiveTex.getState(handler);
        }
    }

    public static void sendFileToFileService(final File file, final AHandler<String> handler) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    handler.onResultRecieved(FileUtils.sendMultipart(file, "http://file-service-0-sdk-prerelease.livetex.ru/upload"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public static void sendFileQueue(String url, final AHandler<Boolean> handler) {
        if (sLiveTex != null) {
            sLiveTex.sendFile(url, handler);

        }
    }

    public static void setName(String name) {
        if (sLiveTex != null) {
            sLiveTex.setName(name);
        }
    }

    public void openChat() {
        dispatchOnUIThread(
            new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(mContext, FragmentEnvironment.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

                    mContext.startActivity(intent);
                }
            }
        );
    }

    public void destroy() {
        sInstance = null;
    }
}