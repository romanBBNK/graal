import java.util.HashMap;

public class TestClass {
    public static void main(String[] args) {
        HashMap<Integer, String> exampleMap = new HashMap<>();
        exampleMap.put(8, "Goodbye, World!");
        System.out.println(exampleMap.get(8));
    }
}
