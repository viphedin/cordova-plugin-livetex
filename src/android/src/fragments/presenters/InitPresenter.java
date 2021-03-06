package ru.simdev.livetex.fragments.presenters;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import java.util.UUID;

import ru.simdev.livetex.LivetexContext;
import ru.simdev.livetex.utils.DataKeeper;


/**
 * Created by user on 28.07.15.
 */
public class InitPresenter {

    private static final String TAG = "Livetex";

    public InitPresenter() {

    }

    public void init(Context context, final String id) {
        Log.w(TAG, "run FirebaseInstanceId");
/*
        String savedRegId = DataKeeper.restoreRegId(context);
        if (!TextUtils.isEmpty(savedRegId)) {
            Log.v(TAG, "Init with previous regId = " + savedRegId);
            LivetexContext.initLivetex(id, savedRegId);
            return;
        }
*/
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        String token = task.getResult().getToken();
                        DataKeeper.saveRegId(context, token);
                        DataKeeper.saveAppId(context, id);
                        LivetexContext.initLivetex(id, token);
                        Log.w(TAG, "Token: " + token);
                    }
                })
                // Только для демо приложения т.к. google-service.json не содержит риальные данные
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "getInstanceId failed");
                        String token = UUID.randomUUID().toString();
                        DataKeeper.saveRegId(context, token);
                        DataKeeper.saveAppId(context, id);
                        LivetexContext.initLivetex(id, token);
                    }
                });
    }

}
