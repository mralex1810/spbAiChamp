package spb_ai_champ;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedOutputStream;

import spb_ai_champ.util.StreamUtil;

public class LocalRunner {
    private final InputStream inputStream;
    private final OutputStream outputStream;
    LocalRunner(String host, int port, String token) throws IOException {
        Socket socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        inputStream = new BufferedInputStream(socket.getInputStream());
        outputStream = new BufferedOutputStream(socket.getOutputStream());
        StreamUtil.writeString(outputStream, token);
        StreamUtil.writeInt(outputStream, 1);
        StreamUtil.writeInt(outputStream, 0);
        StreamUtil.writeInt(outputStream, 1);
        outputStream.flush();
    }

    void run(String[] args) throws IOException {
        MyStrategy myStrategy = new MyStrategy(args);
        //DebugInterface debugInterface = new DebugInterface(inputStream, outputStream);
        while (true) {
            spb_ai_champ.codegame.ServerMessage message = spb_ai_champ.codegame.ServerMessage.readFrom(inputStream);
            if (message instanceof spb_ai_champ.codegame.ServerMessage.GetAction) {
                spb_ai_champ.codegame.ServerMessage.GetAction getActionMessage = (spb_ai_champ.codegame.ServerMessage.GetAction) message;
                new spb_ai_champ.codegame.ClientMessage.ActionMessage(myStrategy.getAction(getActionMessage.getPlayerView())).writeTo(outputStream);
                outputStream.flush();
            } else if (message instanceof spb_ai_champ.codegame.ServerMessage.Finish) {
                break;
            } else if (message instanceof spb_ai_champ.codegame.ServerMessage.DebugUpdate) {
                spb_ai_champ.codegame.ServerMessage.DebugUpdate debugUpdateMessage = (spb_ai_champ.codegame.ServerMessage.DebugUpdate) message;
                //myStrategy.debugUpdate(debugUpdateMessage.getPlayerView(), debugInterface);
                new spb_ai_champ.codegame.ClientMessage.DebugUpdateDone().writeTo(outputStream);
                outputStream.flush();
            } else {
                throw new IOException("Unexpected server message");
            }
        }
    }

    public static void burst() {
        for(int i = 0; i < 10000; i++) {
            MyStrategy myStrategy = new MyStrategy();
            myStrategy.burst();
        }
    }

    public static void main(String[] args) throws IOException {
        burst();
        String host = "127.0.0.1";
        int port = Integer.parseInt(args[0]);
        String token = "0000000000000000";
        String[] strategyArgs = new String[10];
        for (int i = 1; i < 11; i++) {
            strategyArgs[i - 1] = args[i];
        }
        new LocalRunner(host, port, token).run(strategyArgs);
    }
}