import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;

abstract class User {
    protected String username;
    protected String password;

    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public abstract String getRoleSpecificOptions();
}

class AdminUser extends User {
    public AdminUser(String username, String password) {
        super(username, password);
    }

    @Override
    public String getRoleSpecificOptions() {
        return "Admin Options: Manage Members, Add Expenses, Add Deposits.";
    }
}

class RegularUser extends User {
    public RegularUser(String username, String password) {
        super(username, password);
    }

    @Override
    public String getRoleSpecificOptions() {
        return "User Options: View Records.";
    }
}

public class MealManagerGUI {

    private static final String DATABASE_URL = "jdbc:sqlite:meal_manager.db";

    public static Connection initializeDatabase() {
        Connection conn = null;
        try {
            File dbFile = new File("meal_manager.db");

            // Check if database file exists
            if (!dbFile.exists()) {
                System.out.println("Database file not found. Creating a new database...");
                conn = DriverManager.getConnection(DATABASE_URL);
                createDatabase(conn);
            } else {
                System.out.println("Database file exists. Connecting to the database...");
                conn = DriverManager.getConnection(DATABASE_URL);
            }
        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            e.printStackTrace();
        }
        return conn;
    }

    private static void createDatabase(Connection conn) {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                username TEXT UNIQUE NOT NULL,
                password TEXT NOT NULL
            );
        """;

        String createMembersTable = """
            CREATE TABLE IF NOT EXISTS members (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL UNIQUE,
                name TEXT NOT NULL,
                deposit REAL NOT NULL,
                balance REAL NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users (id)
            );
        """;

        String createMealsTable = """
            CREATE TABLE IF NOT EXISTS meals (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                member_id INTEGER NOT NULL,
                meal_count INTEGER NOT NULL,
                date DATE NOT NULL,
                FOREIGN KEY (member_id) REFERENCES members(id)
            );
        """;

        String createExpensesTable = """
            CREATE TABLE IF NOT EXISTS expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT NOT NULL,
                amount REAL NOT NULL,
                date DATE NOT NULL
            );
        """;

        String insertDefaultUser = """
            INSERT OR IGNORE INTO users (name, username, password) VALUES ('Admin', 'admin', 'password123');
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersTable);
            stmt.execute(createMembersTable);
            stmt.execute(createMealsTable);
            stmt.execute(createExpensesTable);
            stmt.execute(insertDefaultUser);

            System.out.println("Database and tables created successfully.");
        } catch (SQLException e) {
            System.out.println("An error occurred while creating the database.");
            e.printStackTrace();
        }
    }

    private static void showRegistrationPage() {
        JFrame registrationFrame = new JFrame("Register");
        registrationFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        registrationFrame.setSize(400, 250);

        JPanel panel = new JPanel(new GridLayout(6, 2));

        JLabel nameLabel = new JLabel("name:");
        JTextField nameField = new JTextField();

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();

        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        JLabel confirmPassLabel = new JLabel("Confirm Password:");
        JPasswordField confirmPassField = new JPasswordField();

        JButton registerButton = new JButton("Register");

        panel.add(nameLabel);
        panel.add(nameField);
        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(confirmPassLabel);
        panel.add(confirmPassField);
        panel.add(new JLabel());
        panel.add(registerButton);

        registrationFrame.add(panel);
        registrationFrame.setVisible(true);

        registerButton.addActionListener(e -> {
            String name = nameField.getText();
            String username = userField.getText();
            String password = new String(passField.getPassword());
            String confirmPassword = new String(confirmPassField.getPassword());

            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(registrationFrame, "Passwords do not match.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            try (Connection conn = initializeDatabase(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO users (name, username, password) VALUES (?, ?, ?)");) {
                stmt.setString(1, name);
                stmt.setString(2, username);
                stmt.setString(3, password);
                stmt.executeUpdate();

                JOptionPane.showMessageDialog(registrationFrame, "Registration successful. You can now log in.", "Success", JOptionPane.INFORMATION_MESSAGE);
                registrationFrame.dispose();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(registrationFrame, "Registration failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static void showLoginPage() {
        JFrame loginFrame = new JFrame("Meal Manager - Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(400, 250);

        JPanel panel = new JPanel(new GridLayout(4, 2));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();

        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);
        panel.add(new JLabel());
        panel.add(loginButton);
        panel.add(new JLabel());
        panel.add(registerButton);

        loginFrame.add(panel);
        loginFrame.setVisible(true);

        loginButton.addActionListener(e -> {
            String username = userField.getText();
            String password = new String(passField.getPassword());

            try (Connection conn = initializeDatabase(); PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE username = ? AND password = ?");) {
                stmt.setString(1, username);
                stmt.setString(2, password);

                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String userRole = "Regular";
                    if (rs.getString("username").equalsIgnoreCase("admin")) {
                        userRole = "Admin";
                    }

                    User user = userRole.equals("Admin") ? new AdminUser(username, password) : new RegularUser(username, password);
                    JOptionPane.showMessageDialog(loginFrame, "Welcome, " + user.getUsername() + ". " + user.getRoleSpecificOptions());
                    loginFrame.dispose();
                    showHomePage(user);
                } else {
                    JOptionPane.showMessageDialog(loginFrame, "Invalid credentials", "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        });

        registerButton.addActionListener(e -> {
            loginFrame.dispose();
            showRegistrationPage();
        });
    }

    private static void showHomePage(User user) {
        JFrame homeFrame = new JFrame("Meal Manager - Home");
        homeFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        homeFrame.setSize(1000, 600);

        JPanel panel = new JPanel(new BorderLayout());

        JTable table = new JTable();
        DefaultTableModel model = new DefaultTableModel();
        model.addColumn("Member Name");
        model.addColumn("Deposit");
        model.addColumn("Balance");
        model.addColumn("Meals");
        model.addColumn("Individual Cost");

        double totalCost = 0;
        double totalMeals = 0;
        double totalDeposits = 0;

        try (Connection conn = initializeDatabase(); Statement stmt = conn.createStatement()) {
            // Calculate totals
            ResultSet costResult = stmt.executeQuery("SELECT IFNULL(SUM(amount), 0) AS totalCost FROM expenses");
            if (costResult.next()) {
                totalCost = costResult.getDouble("totalCost");
            }

            ResultSet mealResult = stmt.executeQuery("SELECT IFNULL(SUM(meal_count), 0) AS totalMeals FROM meals");
            if (mealResult.next()) {
                totalMeals = mealResult.getDouble("totalMeals");
            }

            ResultSet depositResult = stmt.executeQuery("SELECT IFNULL(SUM(deposit), 0) AS totalDeposits FROM members");
            if (depositResult.next()) {
                totalDeposits = depositResult.getDouble("totalDeposits");
            }

            // Fetch member details
            String query = "SELECT m.name, m.deposit, m.balance, IFNULL(SUM(ml.meal_count), 0) AS meals FROM members m " +
                           "LEFT JOIN meals ml ON m.id = ml.member_id GROUP BY m.id";
            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                double individualMeals = rs.getDouble("meals");
                double individualCost = (totalMeals > 0) ? (individualMeals / totalMeals) * totalCost : 0;

                model.addRow(new Object[]{
                    rs.getString("name"),
                    rs.getDouble("deposit"),
                    String.format("%.2f", rs.getDouble("deposit")-individualCost),
                    rs.getInt("meals"),
                    String.format("%.2f", individualCost)
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        table.setModel(model);
        JScrollPane tableScroll = new JScrollPane(table);

        JLabel statsLabel = new JLabel(String.format("Total Mess Cost: %.2f, Meal Rate: %.2f, Mess Balance: %.2f", 
            totalCost, (totalMeals > 0 ? totalCost / totalMeals : 0), totalDeposits - totalCost));
        statsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(1, 4));

        JButton addMemberButton = new JButton("Add Member");
        addMemberButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try (Connection conn = initializeDatabase()) {
                    // Fetch the list of registered users who are not already members
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, name FROM users WHERE id NOT IN (SELECT user_id FROM members);"
                    );

                    ResultSet rs = stmt.executeQuery();

                    // Populate the drop-down list with usernames
                    ArrayList<Integer> userIds = new ArrayList<>();
                    JComboBox<String> userDropdown = new JComboBox<>();

                    while (rs.next()) {
                        userIds.add(rs.getInt("id"));
                        userDropdown.addItem(rs.getString("name"));
                    }

                    if (userIds.isEmpty()) {
                        JOptionPane.showMessageDialog(homeFrame, "No users available to add as members.", "Info", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    // Show the drop-down list to the admin
                    int option = JOptionPane.showConfirmDialog(
                        homeFrame, 
                        userDropdown, 
                        "Select a User to Add as Member", 
                        JOptionPane.OK_CANCEL_OPTION
                    );

                    if (option == JOptionPane.OK_OPTION) {
                        int selectedIndex = userDropdown.getSelectedIndex();
                        int selectedUserId = userIds.get(selectedIndex);

                        // Prompt for deposit amount
                        String depositStr = JOptionPane.showInputDialog(homeFrame, "Enter Initial Deposit:");
                        if (depositStr != null) {
                            try {
                                double deposit = Double.parseDouble(depositStr);

                                // Add the selected user as a member
                                PreparedStatement addMemberStmt = conn.prepareStatement(
                                    "INSERT INTO members (user_id, name, deposit, balance) VALUES (?, ?, ?, ?);"
                                );
                                addMemberStmt.setInt(1, selectedUserId);
                                addMemberStmt.setString(2, (String) userDropdown.getSelectedItem());
                                addMemberStmt.setDouble(3, deposit);
                                addMemberStmt.setDouble(4, deposit);

                                addMemberStmt.executeUpdate();
                                JOptionPane.showMessageDialog(homeFrame, "Member added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);

                                showHomePage(user); // Refresh the page
                                homeFrame.dispose();
                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(homeFrame, "Invalid deposit amount.", "Error", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(homeFrame, "Failed to fetch users or add member: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });


        JButton addMealButton = new JButton("Add Meal");
        addMealButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String memberIdStr = JOptionPane.showInputDialog(homeFrame, "Enter Member ID:");
                String mealCountStr = JOptionPane.showInputDialog(homeFrame, "Enter Meal Count:");

                if (memberIdStr != null && mealCountStr != null) {
                    try {
                        int memberId = Integer.parseInt(memberIdStr);
                        int mealCount = Integer.parseInt(mealCountStr);

                        try (Connection conn = initializeDatabase(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO meals (member_id, meal_count, date) VALUES (?, ?, date('now'));");) {
                            stmt.setInt(1, memberId);
                            stmt.setInt(2, mealCount);

                            stmt.executeUpdate();
                            JOptionPane.showMessageDialog(homeFrame, "Meal added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                            showHomePage(user); // Refresh the page
                            homeFrame.dispose();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(homeFrame, "Failed to add meal: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(homeFrame, "Invalid input for member ID or meal count.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        JButton addDepositButton = new JButton("Add Deposit");
        addDepositButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String memberIdStr = JOptionPane.showInputDialog(homeFrame, "Enter Member ID:");
                String depositStr = JOptionPane.showInputDialog(homeFrame, "Enter Deposit Amount:");

                if (memberIdStr != null && depositStr != null) {
                    try {
                        int memberId = Integer.parseInt(memberIdStr);
                        double deposit = Double.parseDouble(depositStr);

                        try (Connection conn = initializeDatabase(); PreparedStatement stmt = conn.prepareStatement("UPDATE members SET deposit = deposit + ?, balance = balance + ? WHERE id = ?;");) {
                            stmt.setDouble(1, deposit);
                            stmt.setDouble(2, deposit);
                            stmt.setInt(3, memberId);

                            stmt.executeUpdate();
                            JOptionPane.showMessageDialog(homeFrame, "Deposit added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                            showHomePage(user); // Refresh the page
                            homeFrame.dispose();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(homeFrame, "Failed to add deposit: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(homeFrame, "Invalid input for member ID or deposit amount.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        JButton addExpenseButton = new JButton("Add Expense");
        addExpenseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String description = JOptionPane.showInputDialog(homeFrame, "Enter Expense Description:");
                String amountStr = JOptionPane.showInputDialog(homeFrame, "Enter Expense Amount:");

                if (description != null && amountStr != null) {
                    try {
                        double amount = Double.parseDouble(amountStr);

                        try (Connection conn = initializeDatabase(); PreparedStatement stmt = conn.prepareStatement("INSERT INTO expenses (description, amount, date) VALUES (?, ?, date('now'));");) {
                            stmt.setString(1, description);
                            stmt.setDouble(2, amount);

                            stmt.executeUpdate();
                            JOptionPane.showMessageDialog(homeFrame, "Expense added successfully.", "Success", JOptionPane.INFORMATION_MESSAGE);
                            showHomePage(user); // Refresh the page
                            homeFrame.dispose();
                        } catch (SQLException ex) {
                            JOptionPane.showMessageDialog(homeFrame, "Failed to add expense: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(homeFrame, "Invalid input for expense amount.", "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        if (user instanceof RegularUser) {
            addMemberButton.setEnabled(false);
            addMealButton.setEnabled(false);
            addDepositButton.setEnabled(false);
            addExpenseButton.setEnabled(false);
        }

        buttonPanel.add(addMemberButton);
        buttonPanel.add(addMealButton);
        buttonPanel.add(addDepositButton);
        buttonPanel.add(addExpenseButton);

        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(statsLabel, BorderLayout.SOUTH);
        panel.add(buttonPanel, BorderLayout.NORTH);

        homeFrame.add(panel);
        homeFrame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> showLoginPage());
    }
}
