import java.util.HashMap;

public class DangerClass {
    public static void main(String[] args) {
        HashMap<Integer, String> exampleMap = new HashMap<>();
        exampleMap.put(8, "Goodbye, World!");
        System.out.println(exampleMap.getClass());
        System.out.println(exampleMap.get(8));
    }
}