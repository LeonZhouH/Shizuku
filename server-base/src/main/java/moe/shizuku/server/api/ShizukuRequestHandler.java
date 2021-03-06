package moe.shizuku.server.api;

import android.os.Handler;
import android.os.RemoteException;

import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.UUID;

import moe.shizuku.ShizukuState;
import moe.shizuku.io.ParcelInputStream;
import moe.shizuku.io.ParcelOutputStream;
import moe.shizuku.server.ShizukuServer;
import moe.shizuku.server.io.ServerParcelInputStream;
import moe.shizuku.server.io.ServerParcelOutputStream;
import moe.shizuku.server.util.Utils;

/**
 * Created by rikka on 2017/9/23.
 */

public class ShizukuRequestHandler extends RequestHandler {

    private static final String ACTION_REQUEST_STOP = "Shizuku_requestStop";
    private static final String ACTION_AUTHORIZE = "Shizuku_authorize";
    private static final String ACTION_SEND_TOKEN = "Shizuku_sendToken";

    private final Handler mHandler;
    private final UUID mToken;

    public ShizukuRequestHandler(Handler handler, UUID token) {
        mHandler = handler;
        mToken = token;
    }

    public void handle(Socket socket) throws IOException, RemoteException {
        ServerParcelInputStream is = new ServerParcelInputStream(socket.getInputStream());
        ServerParcelOutputStream os = new ServerParcelOutputStream(socket.getOutputStream());
        String action = is.readString();

        switch (action) {
            case ACTION_REQUEST_STOP:
                stop(os, mHandler);
                break;
            case ACTION_AUTHORIZE:
                authorize(is, os, mToken);
                break;
            case ACTION_SEND_TOKEN:
                sendTokenToManger(is, os, mToken);
                break;
            default:
                long most = is.readLong();
                long least = is.readLong();
                if (most != mToken.getMostSignificantBits()
                        && least != mToken.getLeastSignificantBits()) {
                    os.writeException(new SecurityException("unauthorized"));
                    break;
                }

                handle(action, is, os);
                break;
        }

        is.close();
        os.flush();
        os.close();
    }

    public static void authorize(ParcelInputStream is, ParcelOutputStream os, UUID token) throws RemoteException, IOException {
        long most = is.readLong();
        long least = is.readLong();

        os.writeNoException();
        if (Utils.isServerDead()) {
            os.writeParcelable(ShizukuState.createUnavailable());
        } else if (most != token.getMostSignificantBits()
                && least != token.getLeastSignificantBits()) {
            os.writeParcelable(ShizukuState.createUnauthorized());
        } else {
            os.writeParcelable(ShizukuState.createAuthorized());
        }
    }

    private static void stop(ParcelOutputStream os, Handler handler) throws IOException {
        os.writeNoException();

        handler.sendEmptyMessage(ShizukuServer.MESSAGE_EXIT);
    }

    private static void sendTokenToManger(ParcelInputStream is, ParcelOutputStream os, UUID token) throws IOException, RemoteException {
        int uid = is.readInt();
        int userId = uid / 100000;

        ShizukuServer.sendTokenToManger(token, userId);

        os.writeNoException();
    }
}
