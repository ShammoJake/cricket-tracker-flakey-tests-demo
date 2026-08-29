import com.cricket.stats.PlayerTally;
import com.cricket.stats.RecordsBook;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reports the iteration order of the unordered structures the stats module uses.
 *
 * <p>Run it in a number of fresh JVMs and compare the lines. Whatever prints the same
 * order every time cannot make a test flaky on its own, however unordered the type is:
 * String hash codes are fixed by the language, so a HashMap keyed by player id iterates
 * the same way on every run. Only structures whose ordering depends on identity hash
 * codes actually move.
 */
public final class HashOrderProbe {

    private static final String[] IDS = {"IND1", "IND3", "IND5", "AUS2", "AUS4", "AUS8"};

    public static void main(String[] args) {
        System.out.println("stringKeyedMap   " + stringKeyedMap());
        System.out.println("widerKeyedMap    " + widerKeyedMap());
        System.out.println("identitySet      " + identitySet());
        System.out.println("valueHashedSet   " + valueHashedSet());
    }

    /** HashMap<String, ...> over a fixed key set. */
    private static String stringKeyedMap() {
        Map<String, PlayerTally> map = new HashMap<String, PlayerTally>();
        for (String id : IDS) {
            map.put(id, new PlayerTally(id));
        }
        return join(map.keySet());
    }

    /** The same map after further keys have pushed it through a resize. */
    private static String widerKeyedMap() {
        Map<String, PlayerTally> map = new HashMap<String, PlayerTally>();
        for (String id : IDS) {
            map.put(id, new PlayerTally(id));
        }
        for (int i = 0; i < 12; i++) {
            map.put("ENG" + i, new PlayerTally("ENG" + i));
        }
        return join(map.keySet());
    }

    /** HashSet of a type that does not override hashCode, so identity hashing applies. */
    private static String identitySet() {
        Set<PlayerTally> set = new HashSet<PlayerTally>();
        StringBuilder sb = new StringBuilder();
        for (String id : IDS) {
            set.add(new PlayerTally(id));
        }
        for (PlayerTally tally : set) {
            sb.append(tally.getPlayerId()).append(' ');
        }
        return sb.toString().trim();
    }

    /** HashSet of a type whose hashCode is derived from its String fields. */
    private static String valueHashedSet() {
        Set<RecordsBook.Record> set = new HashSet<RecordsBook.Record>();
        StringBuilder sb = new StringBuilder();
        for (String id : IDS) {
            set.add(new RecordsBook.Record(id, "M1", RecordsBook.CENTURY, 100));
        }
        for (RecordsBook.Record record : set) {
            sb.append(record.getPlayerId()).append(' ');
        }
        return sb.toString().trim();
    }

    private static String join(Iterable<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            sb.append(value).append(' ');
        }
        return sb.toString().trim();
    }
}
