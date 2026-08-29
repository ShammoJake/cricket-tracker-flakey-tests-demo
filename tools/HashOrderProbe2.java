import com.cricket.stats.PlayerTally;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * Second probe: does the iteration order move when the elements are allocated on
 * several threads rather than one?
 *
 * <p>The first probe showed that none of the unordered structures move on their own.
 * Identity hash codes come from a per-thread generator, so a single-threaded program
 * lays them out identically on every run. The question here is whether spreading the
 * allocation across a pool -- which is how tallies are really built, one per ingest
 * worker -- is enough to move the order, with the contents still complete and correct.
 */
public final class HashOrderProbe2 {

    private static final String[] IDS = {
        "IND1", "IND3", "IND5", "AUS2", "AUS4", "AUS8", "ENG3", "SA9"};

    public static void main(String[] args) throws InterruptedException {
        System.out.println("identitySetFromPool  " + identitySetFromPool());
        System.out.println("stringMapFromPool    " + stringMapFromPool());
    }

    /** A set of identity-hashed tallies, each allocated on whichever thread got there. */
    private static String identitySetFromPool() throws InterruptedException {
        final Set<PlayerTally> set = Collections.synchronizedSet(new HashSet<PlayerTally>());
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(IDS.length);

        for (final String id : IDS) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        set.add(new PlayerTally(id));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            });
            thread.start();
        }
        start.countDown();
        done.await();

        StringBuilder sb = new StringBuilder();
        synchronized (set) {
            for (PlayerTally tally : set) {
                sb.append(tally.getPlayerId()).append(' ');
            }
        }
        return sb.toString().trim();
    }

    /** The same, but keyed by player id, so the hashes are the String ones. */
    private static String stringMapFromPool() throws InterruptedException {
        final Set<String> set = Collections.synchronizedSet(new HashSet<String>());
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(IDS.length);

        for (final String id : IDS) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        set.add(id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }
            });
            thread.start();
        }
        start.countDown();
        done.await();

        StringBuilder sb = new StringBuilder();
        synchronized (set) {
            for (String id : set) {
                sb.append(id).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
