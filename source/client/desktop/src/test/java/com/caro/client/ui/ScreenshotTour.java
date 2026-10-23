package com.caro.client.ui;

import java.awt.Component;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import javax.imageio.ImageIO;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.caro.client.app.GameClient;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Dev tool: drives the real window against a running server and saves screenshots of the main screens
 * into target/screenshots (handy for the README). An extra headless client plays the opponent.
 *
 * <p>Start the server ({@code cd source && docker compose up -d}), then run this {@code main} from the IDE
 * (it is in the test sources, so use the test classpath).
 */
public final class ScreenshotTour {

    private static final String HOST = "localhost";
    private static final int PORT = 9090;
    private static final File OUT = new File("target/screenshots");

    private ScreenshotTour() {
    }

    public static void main(String[] args) throws Exception {
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        OUT.mkdirs();
        String suffix = UUID.randomUUID().toString().substring(0, 5);

        GameClient me = new GameClient(SwingUtilities::invokeLater);
        MainFrame[] frame = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            frame[0] = new MainFrame(me, HOST, PORT);
            frame[0].setVisible(true);
        });
        pause(800);
        shoot(frame[0], "1-login");

        ExecutorService botUi = Executors.newSingleThreadExecutor();
        GameClient bot = new GameClient(botUi);
        on(botUi, () -> bot.register(HOST, PORT, "binh_" + suffix, "secret1", "Bình"));
        onEdt(() -> me.register(HOST, PORT, "an_" + suffix, "secret1", "An"));
        on(botUi, () -> bot.createRoom("Phòng của Bình"));
        pause(800);
        shoot(frame[0], "2-lobby");

        // I join the bot's room: the bot is host and plays X
        String roomId = botUi.submit(() -> bot.room().id()).get();
        onEdt(() -> me.joinRoom(roomId));
        int[][] botMoves = {{7, 7}, {8, 7}, {9, 7}, {6, 8}, {10, 9}};
        int[][] myMoves = {{7, 8}, {8, 8}, {9, 8}, {10, 8}, {11, 8}};
        for (int i = 0; i < botMoves.length; i++) {
            int[] b = botMoves[i];
            int[] m = myMoves[i];
            on(botUi, () -> bot.move(b[0], b[1]));
            pause(250);
            if (i == 3) {
                shoot(frame[0], "3-playing");
            }
            onEdt(() -> me.move(m[0], m[1]));
            pause(250);
        }
        pause(600);
        on(botUi, bot::rematch);
        pause(600);
        shoot(frame[0], "4-finished");

        String matchId = onEdtValue(() -> me.match().matchId());
        long myId = onEdtValue(() -> me.me().id());
        SwingUtilities.invokeAndWait(() -> frame[0].showHistory(myId, "Lịch sử đấu của bạn"));
        pause(900);
        shootDialog("Lịch sử đấu của bạn", "5-history");
        onEdt(() -> me.matchDetail(matchId).thenAccept(d -> new ReplayDialog(frame[0], d).setVisible(true)));
        pause(900);
        shootDialog("Xem lại trận đấu", "6-replay");
        SwingUtilities.invokeAndWait(frame[0]::showLeaderboard);
        pause(900);
        shootDialog("Bảng xếp hạng", "7-leaderboard");
        closeDialogs();

        // back to the lobby, then the bot invites me
        onEdt(me::leaveRoom);
        on(botUi, bot::leaveRoom);
        pause(500);
        on(botUi, () -> bot.invite(myId));
        pause(900);
        shootDialog("Lời mời chơi", "8-invited");

        System.out.println("Screenshots saved in " + OUT.getAbsolutePath());
        botUi.submit(bot::shutdown).get();
        botUi.shutdownNow();
        SwingUtilities.invokeAndWait(frame[0]::dispose);
    }

    private static void shoot(Component c, String name) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
            c.paintAll(image.createGraphics());
            try {
                ImageIO.write(image, "png", new File(OUT, name + ".png"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void shootDialog(String title, String name) throws Exception {
        for (Window w : Window.getWindows()) {
            if (w instanceof JDialog d && d.isShowing() && title.equals(d.getTitle())) {
                shoot(d.getContentPane(), name);
                return;
            }
        }
        System.out.println("No dialog titled " + title);
    }

    private static void closeDialogs() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog) {
                    w.dispose();
                }
            }
        });
    }

    private static <T> T on(ExecutorService ui, Supplier<CompletableFuture<T>> action) throws Exception {
        return ui.submit(action::get).get().get(10, TimeUnit.SECONDS);
    }

    private static <T> T onEdt(Supplier<CompletableFuture<T>> action) throws Exception {
        CompletableFuture<CompletableFuture<T>> started = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> started.complete(action.get()));
        return started.get(5, TimeUnit.SECONDS).get(10, TimeUnit.SECONDS);
    }

    private static <T> T onEdtValue(Supplier<T> query) throws Exception {
        CompletableFuture<T> value = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> value.complete(query.get()));
        return value.get(5, TimeUnit.SECONDS);
    }

    private static void pause(long ms) throws InterruptedException {
        Thread.sleep(ms);
    }
}
