package com.adobe.phonegap.push;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

public class ZimbraFirebaseApp {

    private static ZimbraFirebaseApp objInstance = null;
    private static FirebaseOptions firebaseOptions = null;
    private static FirebaseApp objFirebase = null;
    private static String senderId = "";
    private ZimbraFirebaseApp(){

    }

    public static void init(Context context, JSONObject FCMConfig) throws JSONException {
        if (objInstance == null) {
            firebaseOptions = new FirebaseOptions.Builder()
                    .setApplicationId(FCMConfig.getString("applicationId"))
                    .setApiKey(FCMConfig.getString("apiKey"))
                    .setProjectId(FCMConfig.getString("projectId"))
                    .setGcmSenderId(FCMConfig.getString("senderId"))
                    .build();

            senderId = FCMConfig.getString("senderId");

            SharedPreferences sharedPref = context.getSharedPreferences(
                    "zimbraMobile",
                    Context.MODE_PRIVATE
            );

            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("senderID", FCMConfig.getString("senderId"));
            editor.apply();

            objFirebase = FirebaseApp.initializeApp(context, firebaseOptions, "[DEFAULT]");
            objInstance = new ZimbraFirebaseApp();
        }
    }
    public static FirebaseInstanceId getInstance() {
        if (objInstance == null) {
            System.out.println("initialize class first");
            return null;
        }
        return FirebaseInstanceId.getInstance();
    }

    public static String getSenderId() {
        return senderId;
    }
}
