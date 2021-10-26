package spb_ai_champ.myClasses;

public class Pair <U, V> implements Comparable<Pair<U, V>>{
    private final U first;
    private final V second;

    public Pair(U first, V second) {
        this.first = first;
        this.second = second;
    }

    public V getSecond() {
        return second;
    }

    public U getFirst() {
        return first;
    }


    @Override
    public int compareTo(Pair<U, V> uvPair) {
        if (first.equals(uvPair.getFirst())) {
            return Integer.compare(first.hashCode(), uvPair.getFirst().hashCode());
        }
        return Integer.compare(second.hashCode(), uvPair.getSecond().hashCode());
    }


    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
