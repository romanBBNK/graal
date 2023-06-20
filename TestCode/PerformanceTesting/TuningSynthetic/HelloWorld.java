import java.util.HashMap;

public class HelloWorld {
    public static void main(String[] args) {

        //Base example. Standard usage, no dangerous calls.
        HashMap<Integer, Integer> mapExample = new HashMap<>(16, 0.75f);

        int max = Integer.parseInt(args[0]);

        for (int i = 0; i < max; i++) {
            mapExample.put(i, i+1);
        }

        //Runtime.getRuntime().gc();

        System.out.println(mapExample.size() + ": " + mapExample.get(max-2));

        //1573867

    }
}