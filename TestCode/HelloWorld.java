import java.util.HashMap;

public class HelloWorld {
    public static void main(String[] args) {
        //Modified initialization. No dangerous calls
        HashMap<Integer, String> exampleMap = new HashMap<>(12, 0.5f);
        exampleMap.put(1, "Hello");
        exampleMap.clear();
        exampleMap.put(1, "Hello");
        exampleMap.put(6, " ");
        exampleMap.put(15, "World!");
        exampleMap.put(8, "!");
        exampleMap.remove(8);
        System.out.println(exampleMap.get(1) + exampleMap.get(6) + exampleMap.get(15));

        messWithMaps();
    }

    public static void messWithMaps(){
        //Modified initialization. Dangerous calls.
        HashMap<Integer, String> exampleMap2 = new HashMap<>(12);
        System.out.println(exampleMap2.getClass().toString());
        System.out.println(exampleMap2 instanceof HashMap);

        //Base example. Standard usage, no dangerous calls.
        HashMap<Integer, String> mapExample = new HashMap<>(16, 0.75f);
        mapExample.put(1, "Hi");
        mapExample.clear();
        mapExample.put(1, "Hi");
        mapExample.put(6, " ");
        mapExample.put(15, "World!");
        mapExample.put(8, "!");
        mapExample.remove(8);
        System.out.println(mapExample.get(1) + mapExample.get(6) + mapExample.get(15));

        for (int i = 0; i < 1573864; i++) {
            mapExample.put(i, "Random entry: " + Math.floor(Math.random() *(1573864 - 0 + 1) + 0));
        }

        for (String s: mapExample.values()) {
            System.out.println(s);
        }
    }

}

//Memory check: valgrind --tool=massif ./helloworld
//massif-visualizer massif.out.XXXXXX

//sudo ./tstime ./helloworld
