import com.nicky.rincewinder.agent.AgentHttpServer;
import com.nicky.rincewinder.agent.Json;
import com.nicky.rincewinder.agent.Sink;

import java.io.File;

/** Spustí jádro Agenta na PC nad zadanou složkou (jen čtení). Slouží k nácviku měřicího skriptu bez těla. */
public class LocalAgent {
    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : ".");
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8808;
        AgentHttpServer s = new AgentHttpServer(port, root, "local-test", new Sink() {
            public void log(String l) {
                System.out.println("[agent] " + l);
            }
        }, new AgentHttpServer.Hooks() {
            public void fillStatus(Json j) {
                j.str("ip", "127.0.0.1").str("mac", "000000000000").str("model", "PC-simulace");
            }

            public String probe(boolean lens) {
                return "simulace sondy (PC), lens=" + lens + "\n";
            }

            public String wakelock(String mode) {
                return "simulace wakelock " + mode;
            }

            public void stop() {
                System.exit(0);
            }
        });
        s.start();
        System.out.println("LocalAgent běží na portu " + s.port() + ", kořen " + root + " (Ctrl+C nebo GET /stop ukončí)");
        Thread.sleep(Long.MAX_VALUE);
    }
}
