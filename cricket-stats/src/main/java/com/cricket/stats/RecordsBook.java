package com.cricket.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notable performances in a series: centuries, five-wicket hauls and the like.
 *
 * <p>Entries are held in a set so the same performance is not recorded twice.
 */
public final class RecordsBook {

    /** One notable performance. */
    public static final class Record {
        private final String playerId;
        private final String matchId;
        private final String kind;
        private final int value;

        public Record(String playerId, String matchId, String kind, int value) {
            if (playerId == null || playerId.trim().isEmpty()) {
                throw new IllegalArgumentException("playerId must not be blank");
            }
            if (kind == null || kind.trim().isEmpty()) {
                throw new IllegalArgumentException("kind must not be blank");
            }
            this.playerId = playerId;
            this.matchId = matchId;
            this.kind = kind;
            this.value = value;
        }

        public String getPlayerId() {
            return playerId;
        }

        public String getMatchId() {
            return matchId;
        }

        /** For example "century" or "five-for". */
        public String getKind() {
            return kind;
        }

        public int getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Record)) {
                return false;
            }
            Record other = (Record) o;
            return value == other.value
                    && playerId.equals(other.playerId)
                    && kind.equals(other.kind)
                    && (matchId == null ? other.matchId == null : matchId.equals(other.matchId));
        }

        @Override
        public int hashCode() {
            int result = playerId.hashCode();
            result = 31 * result + kind.hashCode();
            result = 31 * result + value;
            result = 31 * result + (matchId == null ? 0 : matchId.hashCode());
            return result;
        }

        @Override
        public String toString() {
            return playerId + " " + kind + " (" + value + ")";
        }
    }

    public static final String CENTURY = "century";
    public static final String FIFTY = "fifty";
    public static final String FIVE_FOR = "five-for";

    private final Set<Record> records = new HashSet<Record>();

    /** Records a performance; returns false when it was already present. */
    public boolean record(Record record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        return records.add(record);
    }

    public boolean recordCentury(String playerId, String matchId, int runs) {
        return record(new Record(playerId, matchId, CENTURY, runs));
    }

    public boolean recordFiveFor(String playerId, String matchId, int wickets) {
        return record(new Record(playerId, matchId, FIVE_FOR, wickets));
    }

    public int size() {
        return records.size();
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public List<Record> all() {
        return new ArrayList<Record>(records);
    }

    /** Records of one kind, highest value first. */
    public List<Record> byKind(String kind) {
        List<Record> result = new ArrayList<Record>();
        for (Record record : records) {
            if (record.getKind().equals(kind)) {
                result.add(record);
            }
        }
        Collections.sort(result, new Comparator<Record>() {
            @Override
            public int compare(Record a, Record b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        return result;
    }

    public List<Record> forPlayer(String playerId) {
        List<Record> result = new ArrayList<Record>();
        for (Record record : records) {
            if (record.getPlayerId().equals(playerId)) {
                result.add(record);
            }
        }
        return result;
    }

    public int countOfKind(String kind) {
        return byKind(kind).size();
    }

    /** The highest-valued record of a kind, or null when there is none. */
    public Record highest(String kind) {
        List<Record> ofKind = byKind(kind);
        return ofKind.isEmpty() ? null : ofKind.get(0);
    }

    public void clear() {
        records.clear();
    }
}
