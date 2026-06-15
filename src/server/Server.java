import java.io.*;
import java.net.*;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
    private static final int DEFAULT_TCP_PORT = 6789;
    private static final int DEFAULT_UDP_PORT = 9871;
    private static final int UDP_BUFFER_SIZE = 1024;

    private final int tcpPort;
    private final int udpPort;
    private final Queue<PlayerConnection> waitingPlayers = new ArrayDeque<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger nextPlayerId = new AtomicInteger(1);
    private final AtomicInteger nextMatchId = new AtomicInteger(1);
    private final AtomicInteger activeMatches = new AtomicInteger(0);

    public Server(int tcpPort, int udpPort) {
        this.tcpPort = tcpPort;
        this.udpPort = udpPort;
    }

    public static void main(String[] args) {
        int tcpPort = DEFAULT_TCP_PORT;
        int udpPort = DEFAULT_UDP_PORT;

        if (args.length >= 1) {
            tcpPort = Integer.parseInt(args[0]);
        }

        if (args.length >= 2) {
            udpPort = Integer.parseInt(args[1]);
        }

        Server server = new Server(tcpPort, udpPort);
        server.start();
    }

    public void start() {
        executor.execute(this::startUdpDiscovery);
        startTcpServer();
    }

    private void startUdpDiscovery() {
        try (DatagramSocket socket = new DatagramSocket(udpPort)) {
            socket.setBroadcast(true);
            System.out.println("UDP discovery listening on port " + udpPort);

            while (true) {
                byte[] buffer = new byte[UDP_BUFFER_SIZE];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);

                String message = new String(request.getData(), 0, request.getLength()).trim();
                String response = buildUdpResponse(message);
                byte[] responseData = response.getBytes();

                DatagramPacket reply = new DatagramPacket(
                        responseData,
                        responseData.length,
                        request.getAddress(),
                        request.getPort()
                );

                socket.send(reply);
            }
        } catch (IOException exception) {
            System.err.println("UDP discovery stopped: " + exception.getMessage());
        }
    }

    private String buildUdpResponse(String message) {
        if (message.equalsIgnoreCase("PING")) {
            return "PONG";
        }

        if (message.equalsIgnoreCase("DISCOVER") || message.equalsIgnoreCase("STATUS") || message.isEmpty()) {
            return serverAdvertisement();
        }

        return "ERROR unknown_udp_command expected DISCOVER, STATUS, or PING";
    }

    private String serverAdvertisement() {
        return "NAVAL_BATTLE_SERVER tcp_port=" + tcpPort
                + " udp_port=" + udpPort
                + " waiting_players=" + waitingPlayerCount()
                + " active_matches=" + activeMatches.get();
    }

    private int waitingPlayerCount() {
        synchronized (waitingPlayers) {
            return waitingPlayers.size();
        }
    }

    private void startTcpServer() {
        try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
            System.out.println("TCP match server listening on port " + tcpPort);

            while (true) {
                Socket socket = serverSocket.accept();
                PlayerConnection player = new PlayerConnection(nextPlayerId.getAndIncrement(), socket);
                executor.execute(() -> handleNewPlayer(player));
            }
        } catch (IOException exception) {
            System.err.println("TCP server stopped: " + exception.getMessage());
            executor.shutdownNow();
        }
    }

    private void handleNewPlayer(PlayerConnection player) {
        player.send("WELCOME player=" + player.id);
        player.send("WAITING_FOR_MATCH");

        PlayerConnection opponent = null;

        synchronized (waitingPlayers) {
            while (!waitingPlayers.isEmpty() && opponent == null) {
                PlayerConnection candidate = waitingPlayers.poll();
                if (candidate.isOpen()) {
                    opponent = candidate;
                }
            }

            if (opponent == null) {
                waitingPlayers.offer(player);
                return;
            }
        }

        startMatch(opponent, player);
    }

    private void startMatch(PlayerConnection playerOne, PlayerConnection playerTwo) {
        int matchId = nextMatchId.getAndIncrement();
        activeMatches.incrementAndGet();

        MatchSession match = new MatchSession(matchId, playerOne, playerTwo, activeMatches);
        executor.execute(match);
    }

    private static void closeQuietly(PlayerConnection player) {
        try {
            player.close();
        } catch (IOException ignored) {
        }
    }

    private static class PlayerConnection {
        private final int id;
        private final Socket socket;
        private final BufferedReader input;
        private final PrintWriter output;

        private PlayerConnection(int id, Socket socket) throws IOException {
            this.id = id;
            this.socket = socket;
            this.input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
        }

        private void send(String message) {
            output.println(message);
        }

        private String readLine() throws IOException {
            return input.readLine();
        }

        private boolean isOpen() {
            return !socket.isClosed();
        }

        private void close() throws IOException {
            socket.close();
        }
    }

    private static class MatchSession implements Runnable {
        private final int id;
        private final PlayerConnection playerOne;
        private final PlayerConnection playerTwo;
        private final AtomicInteger activeMatches;
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final GameTable t1;
        private final GameTable t2;
        private int turn;

        private MatchSession(
                int id,
                PlayerConnection playerOne,
                PlayerConnection playerTwo,
                AtomicInteger activeMatches
        ) {
            this.id = id;
            this.playerOne = playerOne;
            this.playerTwo = playerTwo;
            this.activeMatches = activeMatches;
            this.t1 = new GameTable();
            this.t2 = new GameTable();
            this.turn = 1;
        }

        @Override
        public void run() {
            System.out.println("Match " + id + " started: player "
                    + playerOne.id + " vs player " + playerTwo.id);

            playerOne.send("MATCH_START id=" + id + " you= " + playerOne.id + " opponent=" + playerTwo.id);
            playerTwo.send("MATCH_START id=" + id + " you= " + playerTwo.id + " opponent=" + playerOne.id);

            System.out.println("T1 =");
            t1.print();
            System.out.println("T2 =");
            t2.print();

            Thread playerOneRelay = new Thread(() -> relay(playerOne, playerTwo), "match-" + id + "-p1");
            Thread playerTwoRelay = new Thread(() -> relay(playerTwo, playerOne), "match-" + id + "-p2");

            playerOneRelay.start();
            playerTwoRelay.start();

            try {
                playerOneRelay.join();
                playerTwoRelay.join();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                closeMatch();
            }
        }

        private String help() {
            String res = "------------------------\n"
                       + "To attack: [ATTACK x y]\n"
                       + "To quit the match: [QUIT]\n";

            res += "------------------------\n";

            return res;
        }

        private void relay(PlayerConnection from, PlayerConnection to) {
            try {
                String line;
                while (!closed.get() && (line = from.readLine()) != null) {
                    if (line.equalsIgnoreCase("QUIT")) {
                        to.send("OPPONENT_LEFT");
                        break;
                    } else if (line.equalsIgnoreCase("HELP"))  {
                        from.send(help());
                    }
                    else {
                        String[] words = line.split(" ");
                        boolean validTurn = false;

                        if (from.equals(playerOne) && this.turn==1) {
                            validTurn = true;
                        } else if (from.equals(playerTwo) && this.turn==2) {
                            validTurn = true;
                        }

                        if (words[0].equalsIgnoreCase("ATTACK") && validTurn) {
                            if (words.length != 3) {
                                from.send("Invalid attack command");
                                from.send(help());
                                continue;
                            }

                            try {
                                int x = Integer.parseInt(words[1]);
                                int y = Integer.parseInt(words[2]);

                                ShotResult res = null;
                                Boat destroyedBoat = null;

                                if (from.equals(this.playerOne)) {
                                    res = t2.shot(x, y);
                                    if (res == ShotResult.Destroyed || res == ShotResult.GameOver) {
                                        destroyedBoat = t2.lastDestroyedBoat;
                                    }
                                    turn = 2;
                                    System.out.println("T2 =");
                                    t2.print();

                                } else if (from.equals(this.playerTwo)) {
                                    res = t1.shot(x, y);
                                    if (res == ShotResult.Destroyed || res == ShotResult.GameOver) {
                                        destroyedBoat = t1.lastDestroyedBoat;
                                    }
                                    turn = 1;
                                    System.out.println("T1 =");
                                    t1.print();
                                }

                                String resStr = res.toString();
                                if ((res == ShotResult.Destroyed || res == ShotResult.GameOver) && destroyedBoat != null) {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < destroyedBoat.pos.length; i++) {
                                        sb.append(destroyedBoat.pos[i]);
                                        if (i < destroyedBoat.pos.length - 1) sb.append(",");
                                    }
                                    resStr += " " + sb.toString();
                                }

                                from.send(resStr);
                                to.send("You got attacked on: " + x + " " + y);
                                
                                if (res == ShotResult.GameOver) {
                                    to.send("GameOver_Loss");
                                    t1.reset();
                                    t2.reset();
                                    turn = 1;
                                    from.send("MATCH_RESET");
                                    to.send("MATCH_RESET");
                                }

                            } catch (Exception e) {
                                from.send("Invalid attack command");
                                from.send(help());
                            }
                        } else {
                            to.send("OPPONENT " + line);
                        }
                    }

                }
            } catch (IOException exception) {
                to.send("OPPONENT_LEFT");
            } finally {
                closeMatch();
            }
        }

        private void closeMatch() {
            if (closed.compareAndSet(false, true)) {
                closeQuietly(playerOne);
                closeQuietly(playerTwo);
                activeMatches.decrementAndGet();
                System.out.println("Match " + id + " closed");
            }
        }
    }
}
