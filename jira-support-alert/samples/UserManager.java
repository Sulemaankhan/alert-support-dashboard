import java.util.ArrayList;
import java.util.List;

/**
 * A sample class for a coding project containing intentional bugs.
 * Goals: Fix NullPointerExceptions, logic errors, and boundary issues.
 */
public class UserManager {

    private List<String> users;

    public UserManager() {
        // BUG 1: List is declared but never initialized (NullPointerException).
        // To fix: users = new ArrayList<>();
    }

    public void addUser(String name) {
        // BUG 2: No check for null input; might cause issues later.
        users.add(name);
    }

    public String getUser(int index) {
        // BUG 3: IndexOutOfBoundsException. No check if index is valid.
        return users.get(index);
    }

    public boolean findUser(String name) {
        // BUG 4: Comparison error. Using '==' instead of '.equals()' for Strings.
        // BUG 5: Potential NullPointerException if 'name' is null.
        for (String u : users) {
            if (u == name) {
                return true;
            }
        }
        return false;
    }

    public void clearList() {
        // BUG 6: Memory Leak/Logic error. Simply setting to null instead of clearing.
        users = null;
    }

    public static void main(String[] args) {
        UserManager manager = new UserManager();

        // This will crash immediately due to BUG 1
        System.out.println("Adding user...");
        manager.addUser("Alice");

        // This logic check will fail due to BUG 4
        String searchName = new String("Alice");
        System.out.println("Found Alice? " + manager.findUser(searchName));
    }
}
