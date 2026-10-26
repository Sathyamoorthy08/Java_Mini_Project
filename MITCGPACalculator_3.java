import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class MITCGPACalculator_3 {
    private static Scanner sc;

    // Update these values to match your local MySQL setup.
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/mit_cgpa?createDatabaseIfNotExist=true";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Uma@2006";

    private static class GradeEntry {
        String courseName;
        String grade;
        int credits;

        GradeEntry(String courseName, String grade, int credits) {
            this.courseName = courseName;
            this.grade = grade;
            this.credits = credits;
        }
    }

    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    private static void initializeDatabase() {
        try (Connection con = getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS calculation_records (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "register_number VARCHAR(40) NOT NULL," +
                "department VARCHAR(30) NOT NULL," +
                "academic_year INT NOT NULL," +
                "semester INT NOT NULL," +
                "semester_gpa DECIMAL(4,2) NOT NULL," +
                "overall_cgpa DECIMAL(4,2) NOT NULL," +
                "pass_count INT NOT NULL," +
                "fail_count INT NOT NULL," +
                "calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
            );
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS grade_entries (" +
                "id BIGINT PRIMARY KEY AUTO_INCREMENT," +
                "record_id BIGINT NOT NULL," +
                "course_name VARCHAR(255) NOT NULL," +
                "grade VARCHAR(5) NOT NULL," +
                "credits INT NOT NULL," +
                "FOREIGN KEY (record_id) REFERENCES calculation_records(id) ON DELETE CASCADE)"
            );
            System.out.println("Database connected. Entries will be saved.");
        } catch (SQLException e) {
            System.out.println("Database is not available: " + e.getMessage());
            System.out.println("CGPA calculation can continue, but records/statistics need MySQL.");
        }
    }

    private static void saveRecord(String regNo, String department, int year, int semester,
                                   double gpa, double cgpa, List<GradeEntry> grades) {
        int passCount = 0, failCount = 0;
        for (GradeEntry entry : grades) {
            if (gradePoint(entry.grade) == 0) failCount++;
            else passCount++;
        }

        String recordSql = "INSERT INTO calculation_records " +
                "(register_number, department, academic_year, semester, semester_gpa, " +
                "overall_cgpa, pass_count, fail_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String gradeSql = "INSERT INTO grade_entries (record_id, course_name, grade, credits) " +
                "VALUES (?, ?, ?, ?)";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement recordPs = con.prepareStatement(recordSql, Statement.RETURN_GENERATED_KEYS)) {
                recordPs.setString(1, regNo);
                recordPs.setString(2, department);
                recordPs.setInt(3, year);
                recordPs.setInt(4, semester);
                recordPs.setDouble(5, gpa);
                recordPs.setDouble(6, cgpa);
                recordPs.setInt(7, passCount);
                recordPs.setInt(8, failCount);
                recordPs.executeUpdate();

                long recordId;
                try (ResultSet keys = recordPs.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Could not retrieve saved record ID.");
                    recordId = keys.getLong(1);
                }

                try (PreparedStatement gradePs = con.prepareStatement(gradeSql)) {
                    for (GradeEntry entry : grades) {
                        gradePs.setLong(1, recordId);
                        gradePs.setString(2, entry.courseName);
                        gradePs.setString(3, entry.grade);
                        gradePs.setInt(4, entry.credits);
                        gradePs.addBatch();
                    }
                    gradePs.executeBatch();
                }
                con.commit();
                System.out.println("Calculation and course grades saved to database.");
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Could not save this calculation: " + e.getMessage());
        }
    }

    private static String chooseDepartment() {
        System.out.println("\nSelect department:");
        System.out.println("1. CSE  2. IT  3. EI  4. RPT  5. PRODUCTION");
        System.out.println("6. AIDS  7. AERO  8. ECE  9. AUTO");
        System.out.print("Department choice (1-9): ");
        int choice = sc.nextInt();
        switch (choice) {
            case 1: return "CSE";
            case 2: return "IT";
            case 3: return "EI";
            case 4: return "RPT";
            case 5: return "PRODUCTION";
            case 6: return "AIDS";
            case 7: return "AERO";
            case 8: return "ECE";
            case 9: return "AUTO";
            default: return null;
        }
    }

    private static void showStatistics() {
        try (Connection con = getConnection()) {
            System.out.println("\n========== STATISTICS DASHBOARD ==========");
            System.out.println("1. Compare between departments");
            System.out.println("2. Compare between academic years");
            System.out.println("3. Compare between semesters");
            System.out.println("4. Compare between subjects/courses");
            System.out.print("Choose comparison level (1-4): ");
            int level = sc.nextInt();
            if (level < 1 || level > 4) {
                System.out.println("Invalid comparison level.");
                return;
            }
            System.out.println("\nChoose graph type:");
            System.out.println("1. Passed vs failed students");
            System.out.println("2. Total students");
            System.out.println("3. All statistics (total, passed, failed)");
            System.out.print("Graph choice (1-3): ");
            int graphMode = sc.nextInt();
            if (graphMode < 1 || graphMode > 3) {
                System.out.println("Invalid graph choice.");
                return;
            }

            String department = null;
            int year = 0, semester = 0;
            if (level != 1) {
                department = chooseDepartment();
                if (department == null) { System.out.println("Invalid department choice."); return; }
            }
            if (level == 1 || level == 3 || level == 4) {
                System.out.print("Enter year (1-4): ");
                year = sc.nextInt();
                if (year < 1 || year > 4) { System.out.println("Invalid year."); return; }
            }
            if (level == 1 || level == 4) {
                System.out.print("Enter semester (1-2): ");
                semester = sc.nextInt();
                if (semester < 1 || semester > 2) { System.out.println("Invalid semester."); return; }
            }

            List<String> labels = new ArrayList<>();
            List<Integer> totals = new ArrayList<>(), passed = new ArrayList<>(), failed = new ArrayList<>();
            String sql;
            if (level == 1) {
                // Compare departments for a chosen year and semester.
                sql = "SELECT c.department AS grp, COUNT(*) total_students, " +
                    "SUM(CASE WHEN c.fail_count=0 THEN 1 ELSE 0 END) passed_students, " +
                    "SUM(CASE WHEN c.fail_count>0 THEN 1 ELSE 0 END) failed_students " +
                    "FROM calculation_records c JOIN (SELECT register_number, department, academic_year, semester, MAX(id) latest_id " +
                    "FROM calculation_records WHERE academic_year=? AND semester=? GROUP BY register_number, department, academic_year, semester) x " +
                    "ON c.id=x.latest_id GROUP BY c.department ORDER BY c.department";
            } else if (level == 2) {
                // Compare academic years in the selected department.
                sql = "SELECT CONCAT('Year ',c.academic_year) grp, COUNT(*) total_students, " +
                    "SUM(CASE WHEN c.fail_count=0 THEN 1 ELSE 0 END) passed_students, " +
                    "SUM(CASE WHEN c.fail_count>0 THEN 1 ELSE 0 END) failed_students " +
                    "FROM calculation_records c JOIN (SELECT register_number, academic_year, MAX(id) latest_id " +
                    "FROM calculation_records WHERE department=? GROUP BY register_number, academic_year) x ON c.id=x.latest_id " +
                    "WHERE c.department=? GROUP BY c.academic_year ORDER BY c.academic_year";
            } else if (level == 3) {
                // Compare semesters in selected department and year.
                sql = "SELECT CONCAT('Semester ',c.semester) grp, COUNT(*) total_students, " +
                    "SUM(CASE WHEN c.fail_count=0 THEN 1 ELSE 0 END) passed_students, " +
                    "SUM(CASE WHEN c.fail_count>0 THEN 1 ELSE 0 END) failed_students " +
                    "FROM calculation_records c JOIN (SELECT register_number, semester, MAX(id) latest_id " +
                    "FROM calculation_records WHERE department=? AND academic_year=? GROUP BY register_number, semester) x ON c.id=x.latest_id " +
                    "WHERE c.department=? AND c.academic_year=? GROUP BY c.semester ORDER BY c.semester";
            } else {
                // Compare individual subjects/courses in selected department, year, semester.
                sql = "SELECT g.course_name grp, COUNT(DISTINCT c.register_number) total_students, " +
                    "COUNT(DISTINCT CASE WHEN UPPER(TRIM(g.grade)) NOT IN ('RA','U','RA/U') THEN c.register_number END) passed_students, " +
                    "COUNT(DISTINCT CASE WHEN UPPER(TRIM(g.grade)) IN ('RA','U','RA/U') THEN c.register_number END) failed_students " +
                    "FROM grade_entries g JOIN calculation_records c ON c.id=g.record_id " +
                    "JOIN (SELECT register_number, MAX(id) latest_id FROM calculation_records WHERE department=? AND academic_year=? AND semester=? GROUP BY register_number) x " +
                    "ON c.id=x.latest_id AND c.register_number=x.register_number " +
                    "GROUP BY g.course_name ORDER BY g.course_name";
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                if (level == 1) { ps.setInt(1, year); ps.setInt(2, semester); }
                else if (level == 2) { ps.setString(1, department); ps.setString(2, department); }
                else if (level == 3) { ps.setString(1, department); ps.setInt(2, year); ps.setString(3, department); ps.setInt(4, year); }
                else { ps.setString(1, department); ps.setInt(2, year); ps.setInt(3, semester); }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        labels.add(rs.getString("grp"));
                        totals.add(rs.getInt("total_students"));
                        passed.add(rs.getInt("passed_students"));
                        failed.add(rs.getInt("failed_students"));
                    }
                }
            }
            if (labels.isEmpty()) {
                System.out.println("No saved statistics found for the selected filters.");
                return;
            }
            System.out.println("\nSTATISTICS SUMMARY");
            System.out.printf("%-48s %10s %10s %10s%n", "Group", "Total", "Passed", "Failed");
            for (int i=0; i<labels.size(); i++)
                System.out.printf("%-48s %10d %10d %10d%n", labels.get(i), totals.get(i), passed.get(i), failed.get(i));
            final String heading = level == 1 ? "Department comparison" : level == 2 ? "Academic year comparison" : level == 3 ? "Semester comparison" : "Subject/course comparison";
            final String subheading = (department == null ? "All departments" : department) +
                (level == 1 ? " • Year " + year + " • Semester " + semester :
                level == 2 ? " • All academic years" : level == 3 ? " • Year " + year : " • Year " + year + " • Semester " + semester);
            showComparisonChart(heading, subheading, labels, totals, passed, failed, graphMode);
        } catch (SQLException e) {
            System.out.println("Unable to load statistics: " + e.getMessage());
            System.out.println("Check MySQL, database credentials, and Connector/J setup.");
        }
    }

    private static void showComparisonChart(String heading, String subheading, List<String> labels,
            List<Integer> totals, List<Integer> passed, List<Integer> failed, int graphMode) {
        List<String> l = new ArrayList<>(labels);
        List<Integer> t = new ArrayList<>(totals), p = new ArrayList<>(passed), f = new ArrayList<>(failed);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("MIT CGPA Statistics - " + heading);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new ComparisonChartPanel(heading, subheading, l, t, p, f, graphMode));
            frame.setVisible(true);
        });
    }

    private static class ComparisonChartPanel extends JPanel {
        private final String heading, subheading;
        private final List<String> labels;
        private final List<Integer> totals, passed, failed;
        private final int graphMode;
        ComparisonChartPanel(String heading, String subheading, List<String> labels, List<Integer> totals,
                List<Integer> passed, List<Integer> failed, int graphMode) {
            this.heading=heading; this.subheading=subheading; this.labels=labels;
            this.totals=totals; this.passed=passed; this.failed=failed; this.graphMode=graphMode;
            setBackground(new Color(15, 23, 42));
        }
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g=(java.awt.Graphics2D)graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w=getWidth(), h=getHeight(), left=65, top=100, right=30, bottom=155;
            int pw=Math.max(100,w-left-right), ph=Math.max(100,h-top-bottom), max=1;
            for(int i=0;i<labels.size();i++) max=Math.max(max, graphMode==2?totals.get(i):graphMode==1?Math.max(passed.get(i),failed.get(i)):Math.max(totals.get(i),Math.max(passed.get(i),failed.get(i))));
            int step=Math.max(1,(int)Math.ceil(max/5.0)), axis=((max+step-1)/step)*step;
            g.setColor(new Color(226,232,240)); g.setFont(new Font("Segoe UI",Font.BOLD,23)); g.drawString(heading,25,38);
            g.setColor(new Color(148,163,184)); g.setFont(new Font("Segoe UI",Font.PLAIN,14)); g.drawString(subheading,27,64);
            for(int v=0;v<=axis;v+=step){int y=top+ph-(int)(ph*v/(double)axis);g.setColor(new Color(51,65,85));g.drawLine(left,y,left+pw,y);g.setColor(new Color(203,213,225));g.drawString(""+v,25,y+4);}
            int gw=Math.max(1,pw/Math.max(1,labels.size())); int bw=Math.max(5,Math.min(28,gw/4));
            for(int i=0;i<labels.size();i++){
                int cx=left+i*gw+gw/2;
                if(graphMode==2){drawBar(g,cx-bw/2,top,ph,totals.get(i),axis,bw,new Color(59,130,246));}
                else if(graphMode==1){drawBar(g,cx-bw-2,top,ph,passed.get(i),axis,bw,new Color(16,185,129));drawBar(g,cx+2,top,ph,failed.get(i),axis,bw,new Color(239,68,68));}
                else {drawBar(g,cx-bw-2,top,ph,totals.get(i),axis,bw,new Color(59,130,246));drawBar(g,cx,top,ph,passed.get(i),axis,bw,new Color(16,185,129));drawBar(g,cx+bw+2,top,ph,failed.get(i),axis,bw,new Color(239,68,68));}
                g.setColor(new Color(226,232,240));g.setFont(new Font("Segoe UI",Font.PLAIN,10));String label=labels.get(i);if(label.length()>18)label=label.substring(0,17)+"…";
                java.awt.FontMetrics fm=g.getFontMetrics();g.drawString(label,cx-fm.stringWidth(label)/2,top+ph+22);
                g.setColor(new Color(148,163,184));g.drawString("T:"+totals.get(i),cx-18,top+ph+40);
            }
            int ly=h-30, x=w/2-(graphMode==3?150:graphMode==1?90:45);
            if(graphMode!=2){legend(g,x,ly,new Color(16,185,129),"Passed");x+=90;legend(g,x,ly,new Color(239,68,68),"Failed");x+=85;}
            if(graphMode!=1)legend(g,x,ly,new Color(59,130,246),"Total");
            g.dispose();
        }
        private void drawBar(java.awt.Graphics2D g,int x,int top,int ph,int value,int axis,int bw,Color color){int bh=(int)(ph*value/(double)axis);g.setColor(color);g.fillRoundRect(x,top+ph-bh,bw,Math.max(0,bh),5,5);}
        private void legend(java.awt.Graphics2D g,int x,int y,Color c,String label){g.setColor(c);g.fillRect(x,y-10,12,12);g.setColor(new Color(226,232,240));g.setFont(new Font("Segoe UI",Font.PLAIN,12));g.drawString(label,x+17,y);}
    }

    private static class Course {
        String name;
        int credits;
        Course(String name, int credits) {
            this.name = name;
            this.credits = credits;
        }
    }

    private static int gradePoint(String grade) {
        switch (grade.trim().toUpperCase(Locale.ROOT)) {
            case "S": return 10;
            case "A+": return 9;
            case "A": return 8;
            case "B+": return 7;
            case "B": return 6;
            case "C": return 5;
            case "RA":
            case "U":
            case "RA/U": return 0;
            default: return -1;
        }
    }

    private static String readGrade(String courseName) {
        while (true) {
            System.out.print(courseName + "\nENTER YOUR GRADE (S/A+/A/B+/B/C/RA): ");
            String grade = sc.next().trim().toUpperCase(Locale.ROOT);
            if (grade.equals("U") || grade.equals("RA/U")) grade = "RA";
            if (gradePoint(grade) >= 0) return grade;
            System.out.println("Wrong input. Please enter a valid grade.");
        }
    }
private static Course[] getCoursesCSE(int year, int semester) {
        if (year == 1 && semester == 1) {
            return new Course[] {
                new Course("EN23CO1 - FOUNDATION ENGLISH", 3),
                new Course("MA23CO1 - MATRICES AND CALCULUS", 4),
                new Course("PH23CO1 - ENGINEERING PHYSICS", 4),
                new Course("EE23CO1 - FUNDAMENTALS OF ELECTRICAL AND ELECTRONICS ENGINEERING", 3),
                new Course("CS23CO4 - PROGRAMMING IN C", 4),
                new Course("CS231O1 - COMPUTATIONAL THINKING", 2),
                new Course("UC23H01 - HERITAGE OF TAMIL", 1)
            };
        } else if (year == 1 && semester == 2) {
            return new Course[] {
                new Course("EN23CO2 - PROFESSIONAL ENGLISH", 3),
                new Course("MA23C02 - DISCRETE MATHEMATICS", 4),
                new Course("CY23CO1 - ENGINEERING CHEMISTRY", 4),
                new Course("ME23C01 - ENGINEERING DRAWING", 4),
                new Course("ME23CO4 - MAKERSPACE", 3),
                new Course("UC23H02 - TAMIL AND TECHNOLOGY", 1),
                new Course("OBJECT ORIENTED PROGRAMMING USING C++", 3)
            };
        } else if (year == 2 && semester == 1) {
            return new Course[] {
                new Course("MA23CO5 - PROBABILITY AND STATISTICS", 4),
                new Course("CS23301 - SOFTWARE ENGINEERING", 5),
                new Course("CS23302 - DATA STRUCTURES", 5),
                new Course("CS23303 - DIGITAL SYSTEM DESIGN", 5),
                new Course("CS23304 - JAVA PROGRAMMING", 3),
                new Course("CS23U01 - STANDARDS - COMPUTER SCIENCE & ENGINEERING", 1),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2),
                new Course("SKILL DEVELOPMENT COURSE-1", 2)
            };
        } else if (year == 2 && semester == 2) {
            return new Course[] {
                new Course("MA23CO3 - LINEAR ALGEBRA AND NUMERICAL METHODS", 4),
                new Course("CS23401 - DATABASE MANAGEMENT SYSTEMS", 5),
                new Course("CS23402 - COMPUTER ARCHITECTURE", 4),
                new Course("CS23403 - FULL STACK TECHNOLOGIES", 4),
                new Course("CS23404 - DESIGN AND ANALYSIS OF ALGORITHMS", 3),
                new Course("SKILL DEVELOPMENT COURSE-2", 2),
                new Course("INDUSTRY ORIENTED COURSE", 1)
            };
        } else if (year == 3 && semester == 1) {
            return new Course[] {
                new Course("CS23501 - OPERATING SYSTEMS", 5),
                new Course("CS23502 - NETWORKS AND DATA COMMUNICATION", 5),
                new Course("CS23503 - THEORY OF COMPUTATION", 3),
                new Course("PROFESSIONAL ELECTIVE-1", 3),
                new Course("OPEN ELECTIVE-1", 3),
                new Course("CS23L01 - SELF LEARNING COURSE", 1),
                new Course("INDUSTRY ORIENTED COURSE-2", 1),
                new Course("ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("SKILL DEVELOPMENT COURSE-3", 2)
            };
        } else if (year == 3 && semester == 2) {
            return new Course[] {
                new Course("CS23601 - CRYPTOGRAPHY AND NETWORK SECURITY", 4),
                new Course("CS23602 - COMPILER DESIGN", 4),
                new Course("CS23603 - MACHINE LEARNING", 5),
                new Course("PROFESSIONAL ELECTIVE-2", 3),
                new Course("OPEN ELECTIVE-2", 3),
                new Course("PROFESSIONAL ELECTIVE-3", 3),
                new Course("CS23U02 - PERSPECTIVES OF SUSTAINABLE DEVELOPMENT", 3),
                new Course("CREATIVE AND INNOVATIVE PROJECT", 2)
            };
        } else if (year == 4 && semester == 1) {
            return new Course[] {
                new Course("EMERGING TECHNOLOGY COURSE-1", 4),
                new Course("PROFESSIONAL ELECTIVE-4", 3),
                new Course("PROFESSIONAL ELECTIVE-5", 3),
                new Course("PROFESSIONAL ELECTIVE-6", 3),
                new Course("EMERGING TECHNOLOGY COURSE-2", 3),
                new Course("INDUSTRY ORIENTED COURSE-3", 1)
            };
        } else if (year == 4 && semester == 2) {
            return new Course[] {
                new Course("PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursCSE(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CS23D01 - CAPSTONE IDEATION";
            } else if (year == 3 && semester == 2) {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 1";
            } else {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 2";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsCSE(int year, int semester) {
        if (year == 1 && semester == 1) return 21;
        if (year == 1 && semester == 2) return 22;
        if (year == 2 && semester == 1) return 27;
        if (year == 2 && semester == 2) return 23;
        if (year == 3 && semester == 1) return 26 + 6; // 26 course credits + 6 honours
        if (year == 3 && semester == 2) return 27 + 6; // 27 course credits + 6 honours
        if (year == 4 && semester == 1) return 17 + 6; // 17 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesIT(int year, int semester) {
        if (year == 1 && semester == 1) {
            return new Course[] {
                new Course("EN23C01 - FOUNDATION ENGLISH", 3),
                new Course("MA23C01 - MATRICES AND CALCULUS", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELING", 4),
                new Course("EE23C02 - ELECTRICAL AND ELECTRONICS ENGINEERING", 3),
                new Course("ME23C04 - MAKERSPACE", 3),
                new Course("UC23H01 - HERITAGE OF TAMILS", 1)
            };
        } else if (year == 1 && semester == 2) {
            return new Course[] {
                new Course("EN23C02 - PROFESSIONAL COMMUNICATION", 3),
                new Course("MA23C03 - LINEAR ALGEBRA AND NUMERICAL METHODS", 4),
                new Course("PH23C08 - FUNDAMENTALS OF ELECTRONIC MATERIALS AND DEVICES", 3),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("CS23C04 - PROGRAMMING IN C", 4),
                new Course("IT23201 - INFORMATION TECHNOLOGY ESSENTIALS", 4),
                new Course("UC23H02 - TAMILS AND TECHNOLOGY", 1)
            };
        } else if (year == 2 && semester == 1) {
            return new Course[] {
                new Course("MA23C09 - FINITE STATE AUTOMATA AND DISCRETE STRUCTURES", 4),
                new Course("IT23301 - DIGITAL LOGIC AND DESIGN", 4),
                new Course("IT23302 - DATA STRUCTURES", 4),
                new Course("IT23303 - DATABASE MANAGEMENT SYSTEMS", 4),
                new Course("IT23304 - OBJECT ORIENTED PROGRAMMING", 2),
                new Course("IT23U01 - STANDARDS - IT", 1),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2)
            };
        } else if (year == 2 && semester == 2) {
            return new Course[] {
                new Course("MA23C05 - PROBABILITY AND STATISTICS", 4),
                new Course("IT23401 - ADVANCED DATA STRUCTURES", 4),
                new Course("IT23C01 - DESIGN AND ANALYSIS OF ALGORITHMS", 3),
                new Course("IT23402 - COMPUTER ORGANIZATION AND ARCHITECTURE", 3),
                new Course("IT23403 - SOFTWARE ENGINEERING", 3),
                new Course("IT23C02 - OPERATING SYSTEMS", 4),
                new Course("IT23L01 - SELF-LEARNING COURSE", 1),
                new Course("SKILL DEVELOPMENT COURSE I", 2)
            };
        } else if (year == 3 && semester == 1) {
            return new Course[] {
                new Course("IT23501 - COMPUTER NETWORKS", 4),
                new Course("IT23502 - WEB PROGRAMMING", 4),
                new Course("IT23503 - COMPILER DESIGN", 3),
                new Course("IT23504 - MACHINE LEARNING", 4),
                new Course("PROFESSIONAL ELECTIVE I", 3),
                new Course("UC23E01 - ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("INDUSTRY ORIENTED COURSE I", 1),
                new Course("SKILL DEVELOPMENT COURSE II", 2),
                new Course("IT23505 - SOCIETAL ORIENTED PROJECT", 1)
            };
        } else if (year == 3 && semester == 2) {
            return new Course[] {
                new Course("IT23601 - DISTRIBUTED SYSTEMS AND COMPUTING", 3),
                new Course("IT23602 - NATURAL LANGUAGE AND IMAGE PROCESSING", 4),
                new Course("EMERGING TECHNOLOGY COURSE I", 3),
                new Course("PROFESSIONAL ELECTIVE II", 3),
                new Course("PROFESSIONAL ELECTIVE III", 3),
                new Course("OPEN ELECTIVE I", 3),
                new Course("SKILL DEVELOPMENT COURSE III", 2),
                new Course("INDUSTRY ORIENTED COURSE II", 1),
                new Course("IT23U02 - PERSPECTIVES OF SUSTAINABILITY DEVELOPMENT", 3)
            };
        } else if (year == 4 && semester == 1) {
            return new Course[] {
                new Course("IT23701 - CRYPTOGRAPHY AND NETWORK SECURITY", 4),
                new Course("EMERGING TECHNOLOGY COURSE II", 3),
                new Course("PROFESSIONAL ELECTIVE IV", 3),
                new Course("PROFESSIONAL ELECTIVE V", 3),
                new Course("PROFESSIONAL ELECTIVE VI", 3),
                new Course("OPEN ELECTIVE II", 3),
                new Course("INDUSTRY ORIENTED COURSE III", 1),
                new Course("IT23702 - SOFTWARE DEVELOPMENT PROJECT LABORATORY", 2)
            };
        } else if (year == 4 && semester == 2) {
            return new Course[] {
                new Course("IT23801 - PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursIT(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CS23D01 - CAPSTONE IDEATION";
            } else if (year == 3 && semester == 2) {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 1";
            } else {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 2";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsIT(int year, int semester) {
        if (year == 1 && semester == 1) return 22;
        if (year == 1 && semester == 2) return 23;
        if (year == 2 && semester == 1) return 21;
        if (year == 2 && semester == 2) return 24;
        if (year == 3 && semester == 1) return 25 + 6; // 25 course credits + 6 honours
        if (year == 3 && semester == 2) return 25 + 6; // 25 course credits + 6 honours
        if (year == 4 && semester == 1) return 22 + 6; // 22 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesEI(int year, int semester) {
        if (year == 1 && semester == 1) {
            return new Course[] {
                new Course("EN23C03 - FOUNDATION ENGLISH", 3),
                new Course("MA23C11 - CALCULUS", 4),
                new Course("PH23C11 - ENGINEERING PHYSICS", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("ME23C03 - ENGINEERING MECHANICS", 4),
                new Course("ME23C04 - MAKERSPACE", 3),
                new Course("UC23H01 - HERITAGE OF TAMILS", 1)
            };
        } else if (year == 1 && semester == 2) {
            return new Course[] {
                new Course("EN23C04 - PROFESSIONAL COMMUNICATION", 3),
                new Course("MA23C02 - ORDINARY DIFFERENTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("PH23C10 - ELECTRONIC PROPERTIES OF MATERIALS", 3),
                new Course("CY23C10 - ENGINEERING CHEMISTRY", 4),
                new Course("CS23C04 - PROGRAMMING IN C", 4),
                new Course("AU23201 - THERMODYNAMICS AND FLUID MECHANICS", 4),
                new Course("UC23H02 - TAMILS AND TECHNOLOGY", 1)
            };
        } else if (year == 2 && semester == 1) {
            return new Course[] {
                new Course("MA23C03 - LINEAR ALGEBRA AND NUMERICAL METHODS", 4),
                new Course("EI23301 - ANALYSIS OF ELECTRIC CIRCUITS", 4),
                new Course("EI23302 - ELECTRONICS FOR ANALOG SIGNAL PROCESSING - I", 4),
                new Course("EI23303 - BASICS OF SIGNALS AND SYSTEMS", 4),
                new Course("EI23304 - ELECTRICAL MACHINES", 4),
                new Course("EI23U01 - STANDARDS - E & I", 1),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2),
                new Course("SKILL DEVELOPMENT COURSE I", 2)
            };
        } else if (year == 2 && semester == 2) {
            return new Course[] {
                new Course("MA23C05 - PROBABILITY AND STATISTICS", 4),
                new Course("EI23401 - ELECTRONICS FOR ANALOG SIGNAL PROCESSING-II", 4),
                new Course("EI23402 - DIGITAL SYSTEMS", 4),
                new Course("EI23403 - INSTRUMENT TRANSDUCERS", 4),
                new Course("EI23404 - ELECTRICAL AND ELECTRONIC MEASUREMENTS", 4),
                new Course("EI23405 - UNIT OPERATIONS IN PROCESS INDUSTRIES", 3),
                new Course("SKILL DEVELOPMENT COURSE II", 2),
                new Course("EI23L01 - SELF-LEARNING COURSE", 1)
            };
        } else if (year == 3 && semester == 1) {
            return new Course[] {
                new Course("EI23501 - INDUSTRIAL INSTRUMENTATION - I", 4),
                new Course("EI23502 - DISCRETE TIME SIGNAL PROCESSING", 3),
                new Course("EI23503 - CONTROL SYSTEM ANALYSIS AND DESIGN", 3),
                new Course("PROFESSIONAL ELECTIVE - I", 3),
                new Course("PROFESSIONAL ELECTIVE - II", 3),
                new Course("EI23504 - EMBEDDED SYSTEM DESIGN", 3),
                new Course("INDUSTRY ORIENTED COURSE - I", 1),
                new Course("UC23E01 - ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("SKILL DEVELOPMENT COURSE III", 2)
            };
        } else if (year == 3 && semester == 2) {
            return new Course[] {
                new Course("EI23601 - INDUSTRIAL INSTRUMENTATION-II", 4),
                new Course("EI23602 - PROCESS CONTROL", 4),
                new Course("EI23603 - INDUSTRIAL DATA COMMUNICATION", 3),
                new Course("PROFESSIONAL ELECTIVE - III", 3),
                new Course("EI23604 - INDUSTRIAL AUTOMATION SYSTEMS", 3),
                new Course("INDUSTRY ORIENTED COURSE - II", 1),
                new Course("OPEN ELECTIVE - I", 3),
                new Course("EI23U02 - SUSTAINABILITY COURSE", 3)
            };
        } else if (year == 4 && semester == 1) {
            return new Course[] {
                new Course("EMERGING TECHNOLOGY COURSE - I", 3),
                new Course("EMERGING TECHNOLOGY COURSE - II", 3),
                new Course("PROFESSIONAL ELECTIVE - IV", 3),
                new Course("PROFESSIONAL ELECTIVE - V", 3),
                new Course("PROFESSIONAL ELECTIVE - VI", 3),
                new Course("INDUSTRY ORIENTED COURSE - III", 1),
                new Course("OPEN ELECTIVE - II", 3),
                new Course("EI23701 - SUMMER INTERNSHIP / MINI PROJECT", 2)
            };
        } else if (year == 4 && semester == 2) {
            return new Course[] {
                new Course("EI23801 - PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursEI(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CS23D01 - CAPSTONE IDEATION";
            } else if (year == 3 && semester == 2) {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 1";
            } else {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 2";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsEI(int year, int semester) {
        if (year == 1 && semester == 1) return 23;
        if (year == 1 && semester == 2) return 23;
        if (year == 2 && semester == 1) return 25;
        if (year == 2 && semester == 2) return 26;
        if (year == 3 && semester == 1) return 25 + 6; // 25 course credits + 6 honours
        if (year == 3 && semester == 2) return 24 + 6; // 24 course credits + 6 honours
        if (year == 4 && semester == 1) return 21 + 6; // 21 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesRPT(int year, int semester) 
    {
        if (year == 1 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("EN23CO1 - FOUNDATION ENGLISH", 3),
                new Course("MA23CO1 - MATRICES AND CALCULUS", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("CS23C04 - PROGRAMMING IN C", 4),
                new Course("UC23H01 - HERTIAGE OF TAMIL", 1)
            };
        } 
        else if (year == 1 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("EN23CO2 - PROFESSIONAL ENGLISH", 3),
                new Course("MA23C02 - ORDINARY DIFFERNTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("PH23C05 - MATERIAL SCIENCE FOR TECHNOLOGY", 3),
                new Course("ME23C03 - ENGINEERING MECHANICS", 4),
                new Course("ME23CO4 - MAKERSPACE", 3),
                new Course("UC23H02 - TAMIL AND TECHNOLOGY", 1),
                new Course("CY23C06 - PHYSICAL AND ORGANIC CHEMISTRY", 3),
                new Course("EE23C03 - BASICS OF ELECTRICAL AND ELECTRONICS ENGINEERING", 3)
            };
        } 
        else if (year == 2 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("MA23C05 - PROBABILITY AND STATISTICS", 4),
                new Course("AE23C01 - SOLID MECHANICS", 4),
                new Course("RP23301 - ENGINEERING THERMODYNAMICS", 3),
                new Course("RP23302 - INTRODUCTION TO CHEMICAL ENGINEERING", 3),
                new Course("RP23303 - PRINCIPLES OF POLYMERIZATION", 4),
                new Course("RP23304 - FLUID MECHANICS AND FLUID MACHINERY", 3),
                new Course("RP23305 - CAD MODELLING LABORATORY", 1)
            };
        } 
        else if (year == 2 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("RP23401 - PLASTIC MATERIALS", 4),
                new Course("RP23402 - RUBBER MATERIALS", 4),
                new Course("RP23403 - POLYMER COMPOSITES AND TECHNOLOGY", 4),
                new Course("RP23404 - PHYSICAL PROPERTIES OF POLYMERS", 3),
                new Course("RP23405 - DESIGN OF MACHINE ELEMENTS", 3),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2),
                new Course("RP23U01 - STANDARDS RUBBER AND PLASTIC TECHNOLOGY", 1),
                new Course("SKILL DEVELOPMENT COURSE-1", 2),
                new Course("RP23505 - SUMMER INTERNSHIP 1", 0)
            };
        } 
        else if (year == 3 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("RP23501 - RUBBER PROCESSING", 4),
                new Course("RP23502 - PLASTICS PROCESSING", 4),
                new Course("RP23503 - POLYMER CHARACTERIZATION TECHNIQUES", 4),
                new Course("RP23504 - RUBBER COMPOUNDING", 3),
                new Course("PROFESSIONAL ELECTIVE 1", 3),
                new Course("PROFESSIONAL ELECTIVE 2", 3),
                new Course("RP23U02 - PERSPECTIVES OF SUSTAINABLE DEVELOPMENT IN RPT ENIGNEERING", 3),
                new Course("SKILL DEVELOPMENT COURSE-2", 2),
                new Course("INDUSTRY ORIENTED COURSE-1", 1),
                new Course("RP23704 - SUMMER INTERNSHIP 1", 1)
            };
        } 
        else if (year == 3 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("RP23L01 - SELF LEARNING COURSE", 1),
                new Course("UC23E01 - ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("PROFESSIONAL ELECTIVE-3", 3),
                new Course("PROFESSIONAL ELECTIVE-4", 3),
                new Course("PROFESSIONAL ELECTIVE-5", 3),
                new Course("OPEN ELECTIVE-1", 3),
                new Course("EMERGING TECHNOLOGY COURSE-1", 3),
                new Course("SKILL DEVELOPMENT COURSE-3", 2),
                new Course("INDUSTRY ORIENTED COURSE-2", 1),
                new Course("RP23704 - SUMMER INTERNSHIP 2", 0)
            };
        } 
        else if (year == 4 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("EMERGING TECHNOLOGY COURSE-2", 3),
                new Course("PROFESSIONAL ELECTIVE-6", 3),
                new Course("RP23701 - POLYMER TESTING", 4),
                new Course("OPEN ELECTIVE-2", 3),
                new Course("RP23702 - TYRE TECHNOLOGY", 3),
                new Course("RP23703 - REQULATIONS AND  POLYMER INDUSTIRES ", 3),
                new Course("INDUSTRY ORIENTED COURSE-3", 1),
                new Course("RP23704 - SUMMER INTERNSHIP 2", 2)
            };
        } 
        else if (year == 4 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("RP23801 - PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursRPT(int year, int semester) 
    {
        if (!(year == 3 || (year == 4 && semester == 1))) 
        {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone): ");
        int type = sc.nextInt();

        if (type == 1) 
        {
            String courseName;
            if (year == 3 && semester == 1) 
            {
                courseName = "RP23D01 - CAPSTONE DESIGN PROJECT LEVEL 1";
            } 
            else if (year == 3 && semester == 2) 
            {
                courseName = "RP23D02 - CAPSTONE DESIGN PROJECT LEVEL 2";
            } 
            else 
            {
                courseName = "RP23D03 - CAPSTONE DESIGN PROJECT LEVEL 3";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } 
        /*else if (type == 2) 
        {
            String first;
            String second;

            if (year == 3 && semester == 1) 
            {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } 
            else if (year == 3 && semester == 2) 
            {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } 
            else 
            {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        }*/ 
        else 
        {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsRPT(int year, int semester) 
    {
        if (year == 1 && semester == 1) return 21;
        if (year == 1 && semester == 2) return 22;
        if (year == 2 && semester == 1) return 27;
        if (year == 2 && semester == 2) return 23;
        if (year == 3 && semester == 1) return 26 + 6; // 26 course credits + 6 honours
        if (year == 3 && semester == 2) return 27 + 6; // 27 course credits + 6 honours
        if (year == 4 && semester == 1) return 17 + 6; // 17 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesPRODUCTION(int year, int semester) 
    {
        if (year == 1 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("EN23CO1 - FOUNDATION ENGLISH", 3),
                new Course("MA23CO1 - MATRICES AND CALCULUS", 4),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("EE23C04 - ELECTRICAL,ELECTRONICES AND MEASUREMNETS ENGINEERING ", 4),
                new Course("CS23C02 - COMPUTER PROGRAMMING IN PYTHON", 4),
                new Course("UC23H01 - HERTIAGE OF TAMIL", 1)
            };
        } 
        else if (year == 1 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("EN23CO2 - PROFESSIONAL ENGLISH", 3),
                new Course("MA23C02 - ORDINARY DIFFERNTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("ME23C03 - ENGINEERING MECHANICS", 4),
                new Course("ME23CO4 - MAKERSPACE", 3),
                new Course("UC23H02 - TAMIL AND TECHNOLOGY", 1),
                new Course("PR23201 - ENGINEERING  MATERIALS", 4)
            };
        } 
        else if (year == 2 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("MA23C08 - NUMERICAL METHODS", 4),
                new Course("AU23C02 - MECHANICS OF SOLIDS", 4),
                new Course("PR23C01 - THEORY OF MACHINES", 4),
                new Course("AE23C08 - FLUID MECHANICS AND FLUID MACHINERY", 4),
                new Course("PR23301 - FOUNDRY AND WELDING TECHNOLOGY", 4),
                new Course("PR23302 - METAL FORMING", 4),
                new Course("SKILL DEVELOPMENT COURSE-1", 2)
            };
        } 
        else if (year == 2 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("PR23401 - MACHINE COMPONENT AND DESIGN", 4),
                new Course("PR23402 - CNC MACHINES AND AUTOMATION PRODUCTION SYSTEMS", 4),
                new Course("PR23403 - MACHINE PROCESSES", 4),
                new Course("PR23404 - STATISTICES FOR PRODUCTION MANAGEMENT", 3),
                new Course("AU23C01 - APPLIED THERMODYNAMICS", 4),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2),
                new Course("PR23405 - MATERIAL PROCESSING LABORATORY", 1),
                new Course("SKILL DEVELOPMENT COURSE-2", 2),
                new Course("INDUSTRY ORIENTED COURSE-1", 1)
            };
        } 
        else if (year == 3 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("PR23501 - QUANTITATIVE TECHINIQUES IN MANAGEMENT ", 4),
                new Course("PR23502 - COMPUTER AIDED DESIGN AND ANALYSIS", 5),
                new Course("PR23503 - MECHANICAL MEASUREMENTS AND METROLOGY", 4),
                new Course("PR23C04 - UNCONVENTIONAL MACHINING TECHNOLOGIES", 3),
                new Course("PR23504 - FLUID POWER SYSTEMS", 4),
                new Course("PR23U02 - PERSPECTIVES OF SUSTAINABLE DEVELOPMENT IN PRODUCTION ENGINEERING", 3),
                new Course("SKILL DEVELOPMENT COURSE-3", 2),
                new Course("INDUSTRY ORIENTED COURSE-2", 1)
            };
        } 
        else if (year == 3 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("PR23L01 - SELF LEARNING COURSE", 1),
                new Course("PROFESSIONAL ELECTIVE-1", 3),
                new Course("PROFESSIONAL ELECTIVE-2", 3),
                new Course("PROFESSIONAL ELECTIVE-3", 3),
                new Course("PROFESSIONAL ELECTIVE-4", 3),
                new Course("PROFESSIONAL ELECTIVE-5", 3),
                new Course("OPEN ELECTIVE-1", 3),
                new Course("EMERGING TECHNOLOGY COURSE-1", 3),
                new Course("INDUSTRY ORIENTED COURSE-3", 1)
            };
        } 
        else if (year == 4 && semester == 1) 
        {
            return new Course[] 
            {
                new Course("EMERGING TECHNOLOGY COURSE-2", 3),
                new Course("PROFESSIONAL ELECTIVE-6", 3),
                new Course("PR23701 - MECHATRONICS", 4),
                new Course("OPEN ELECTIVE-2", 3),
                new Course("UC23E01 - ENDINEERING ENTERPRENEURSHIP DEVELOPMENT", 3),
                new Course("PR23U01 - STANDARDS FOR PRODUCTION ENGINEERING", 1),
                new Course("PR23702 - ADVANCED MANUFACTURING PROCESSES", 1),
                new Course("PR23703 - COMPREHENSION", 1),
                new Course("PR23704 - MINI PROJECT", 2),
                new Course("PR23705 - SUMMER INTERNSHIP", 1)
            };
        } 
        else if (year == 4 && semester == 2) 
        {
            return new Course[] 
            {
                new Course("PR23801 - PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursPRODUCTION(int year, int semester) 
    {
        if (!(year == 3 || (year == 4 && semester == 1))) 
        {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) 
        {
            String courseName;
            if (year == 3 && semester == 1) 
            {
                courseName = "PR23D01 - CAPSTONE DESIGN PROJECT LEVEL 1";
            } 
            else if (year == 3 && semester == 2) 
            {
                courseName = "PR23D02 - CAPSTONE DESIGN PROJECT LEVEL 2";
            } 
            else 
            {
                courseName = "PR23D03 - CAPSTONE DESIGN PROJECT LEVEL 3";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } 
        else if (type == 2) 
        {
            String first;
            String second;

            if (year == 3 && semester == 1) 
            {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } 
            else if (year == 3 && semester == 2) 
            {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } 
            else 
            {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } 
        else 
        {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsPRODUCTION(int year, int semester) 
    {
        if (year == 1 && semester == 1) return 21;
        if (year == 1 && semester == 2) return 22;
        if (year == 2 && semester == 1) return 27;
        if (year == 2 && semester == 2) return 23;
        if (year == 3 && semester == 1) return 26 + 6; // 26 course credits + 6 honours
        if (year == 3 && semester == 2) return 27 + 6; // 27 course credits + 6 honours
        if (year == 4 && semester == 1) return 17 + 6; // 17 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesAIDS(int year, int semester) {
        if (year == 1 && semester == 1) {           // Semester I = 23
            return new Course[] {
                new Course("MA25C01 - APPLIED CALCULUS", 4),
                new Course("EN25C01 - ENGLISH ESSENTIALS - I", 2),
                new Course("UC25H01 - HERITAGE OF TAMILS", 1),
                new Course("PH25C01 - APPLIED PHYSICS - I", 3),
                new Course("CY25C01 - APPLIED CHEMISTRY - I", 3),
                new Course("CS25C01 - COMPUTER PROGRAMMING: C", 3),
                new Course("CS25C03 - ESSENTIALS OF COMPUTING", 3),
                new Course("ME25C04 - MAKERSPACE", 2),
                new Course("UC25A01 - LIFE SKILLS FOR ENGINEERS - I", 1),
                new Course("UC25A02 - PHYSICAL EDUCATION - I", 1)
            };
        } else if (year == 1 && semester == 2) {    // Semester II = 26
            return new Course[] {
                new Course("MA25C02 - LINEAR ALGEBRA", 4),
                new Course("EE25C01 - BASIC ELECTRICAL AND ELECTRONICS ENGINEERING", 3),
                new Course("CS25C06 - DIGITAL PRINCIPLES AND COMPUTER ORGANIZATION", 4),
                new Course("UC25H02 - TAMILS AND TECHNOLOGY", 1),
                new Course("PH25C03 - APPLIED PHYSICS (CSIE) - II", 3),
                new Course("AD25201 - PYTHON FOR DATA SCIENCE", 4),
                new Course("EN25C02 - ENGLISH ESSENTIALS - II", 2),
                new Course("ME25C05 - RE-ENGINEERING FOR INNOVATION", 2),
                new Course("UC25A03 - LIFE SKILLS FOR ENGINEERS - II", 1),
                new Course("UC25A04 - PHYSICAL EDUCATION - II", 1),
                new Course("FOREIGN LANGUAGE (DEUTSCH / JAPANESE / KOREAN)", 1)
            };
        } else if (year == 2 && semester == 1) {    // Semester III = 25
            return new Course[] {
                new Course("MA25C14 - DISCRETE MATHEMATICS", 4),
                new Course("CS25C08 - DATA STRUCTURES", 5),
                new Course("CS25C09 - JAVA PROGRAMMING", 5),
                new Course("AD25C01 - EXPLORATORY DATA ANALYSIS", 4),
                new Course("CS25C11 - OPERATING SYSTEMS", 4),
                new Course("SKILL DEVELOPMENT COURSE-1", 2),
                new Course("EN25C03 - ENGLISH COMMUNICATION SKILLS LABORATORY - I", 1)
            };
        } else if (year == 2 && semester == 2) {    // Semester IV = 22
            return new Course[] {
                new Course("MA25C13 - PROBABILITY AND STATISTICS", 4),
                new Course("CS25C12 - ALGORITHMS", 3),
                new Course("AD25401 - ARTIFICIAL INTELLIGENCE ESSENTIALS", 3),
                new Course("AD25402 - DATA PRIVACY AND SECURITY", 3),
                new Course("AD25403 - STANDARDS IN ARTIFICIAL INTELLIGENCE", 1),
                new Course("CS25C13 - DATABASE MANAGEMENT SYSTEMS", 5),
                new Course("SKILL DEVELOPMENT COURSE-2", 2),
                new Course("EN25C04 - ENGLISH COMMUNICATION SKILLS LABORATORY - II", 1)
            };
        } else if (year == 3 && semester == 1) {    // Semester V = 25 (+ honours)
            return new Course[] {
                new Course("OPTIMISATION TECHNIQUES", 2),
                new Course("NATURAL LANGUAGE PROCESSING", 3),
                new Course("PROGRAMME ELECTIVE-1", 3),
                new Course("PROGRAMME ELECTIVE-2", 3),
                new Course("MACHINE LEARNING", 4),
                new Course("COMPUTER NETWORKS", 4),
                new Course("INTERNET OF THINGS", 3),
                new Course("SKILL DEVELOPMENT COURSE-3", 2),
                new Course("INDUSTRY ORIENTED COURSE-1", 1)
            };
        } else if (year == 3 && semester == 2) {    // Semester VI = 23 (+ honours)
            return new Course[] {
                new Course("COMPILER DESIGN", 3),
                new Course("PROGRAMME ELECTIVE-3", 3),
                new Course("OPEN ELECTIVE", 3),
                new Course("DEEP LEARNING", 4),
                new Course("IMAGE PROCESSING", 3),
                new Course("UI/UX DESIGN AND HUMAN CENTERED DESIGN", 3),
                new Course("INDUSTRY ORIENTED COURSE-2", 1),
                new Course("FULL STACK DEVELOPMENT LABORATORY", 2),
                new Course("SELF-LEARNING COURSE", 1)
            };
        } else if (year == 4 && semester == 1) {    // Semester VII = 15 (+ honours)
            return new Course[] {
                new Course("PROGRAMME ELECTIVE-4", 3),
                new Course("PROGRAMME ELECTIVE-5", 3),
                new Course("CLIMATE CHANGE AND SUSTAINABILITY", 2),
                new Course("ETHICAL HACKING AND PENETRATION TESTING", 3),
                new Course("ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("SUMMER INTERNSHIP", 1)
            };
        } else if (year == 4 && semester == 2) {    // Semester VIII = 8
            return new Course[] {
                new Course("PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursAIDS(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (0 = not taking honours, 1 = capstone design project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 0) {
            return new double[] {0.0, 0};
        } else if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CAPSTONE DESIGN PROJECT - LEVEL I";
            } else if (year == 3 && semester == 2) {
                courseName = "CAPSTONE DESIGN PROJECT - LEVEL II";
            } else {
                courseName = "CAPSTONE DESIGN PROJECT - LEVEL III";
            }

            int credits = getCapstoneCreditsAIDS(year, semester);
            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * credits;
            System.out.printf("Honours GPA: %.2f%n", points / credits);
            return new double[] {points, credits};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsAIDS(int year, int semester) {
        if (year == 1 && semester == 1) return 23;
        if (year == 1 && semester == 2) return 26;
        if (year == 2 && semester == 1) return 25;
        if (year == 2 && semester == 2) return 22;
        if (year == 3 && semester == 1) return 25;
        if (year == 3 && semester == 2) return 23;
        if (year == 4 && semester == 1) return 15;
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static int getCapstoneCreditsAIDS(int year, int semester) {
        return 6;
    }

private static Course[] getCoursesAERO(int year, int semester) {
        if (year == 1 && semester == 1) {           // Semester I = 23
            return new Course[] {
                new Course("EN23C01 - FOUNDATION ENGLISH", 3),
                new Course("MA23C01 - MATRICES AND CALCULUS", 4),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("EI23C05 - BASICS OF ELECTRONICS AND INSTRUMENTATION ENGINEERING", 3),
                new Course("CS23C04 - PROGRAMMING IN C", 4),
                new Course("UC23H01 - HERITAGE OF TAMILS", 1)
            };
        } else if (year == 1 && semester == 2) {    // Semester II = 24
            return new Course[] {
                new Course("EN23C02 - PROFESSIONAL COMMUNICATION", 3),
                new Course("MA23C02 - ORDINARY DIFFERENTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("ME23C04 - MAKERSPACE", 3),
                new Course("ME23C03 - ENGINEERING MECHANICS", 4),
                new Course("ME23C06 - DESIGN THINKING", 3),
                new Course("UC23H02 - TAMILS AND TECHNOLOGY", 1),
                new Course("SKILL DEVELOPMENT COURSE-1", 2)
            };
        } else if (year == 2 && semester == 1) {    // Semester III = 26
            return new Course[] {
                new Course("MA23C06 - PARTIAL DIFFERENTIAL EQUATIONS AND COMPLEX FUNCTIONS", 4),
                new Course("CS23C02 - COMPUTER PROGRAMMING IN PYTHON", 4),
                new Course("AE23301 - AERO ENGINEERING THERMODYNAMICS", 4),
                new Course("AE23C01 - SOLID MECHANICS", 4),
                new Course("AE23C08 - FLUID MECHANICS AND FLUID MACHINES", 4),
                new Course("AE23302 - AIRCRAFT SYSTEMS & COMPONENTS", 3),
                new Course("INDUSTRY ORIENTED COURSE-1", 1),
                new Course("SKILL DEVELOPMENT COURSE-2", 2)
            };
        } else if (year == 2 && semester == 2) {    // Semester IV = 27
            return new Course[] {
                new Course("MA23C08 - NUMERICAL METHODS", 4),
                new Course("AE23401 - ADVANCED SOLID MECHANICS", 3),
                new Course("AE23402 - CONTROL ENGINEERING FOR AIRCRAFT", 3),
                new Course("AE23403 - AIRPLANE PERFORMANCE", 3),
                new Course("AE23404 - LOW SPEED AERODYNAMICS", 4),
                new Course("PROFESSIONAL ELECTIVE-1", 3),
                new Course("OPEN ELECTIVE-1", 3),
                new Course("INDUSTRY ORIENTED COURSE-2", 1),
                new Course("AE23U01 - AIRWORTHINESS CERTIFICATIONS AND STANDARDS", 1),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2)
            };
        } else if (year == 3 && semester == 1) {    // Semester V = 23 (+ honours)
            return new Course[] {
                new Course("AE23501 - AIR BREATHING PROPULSION", 4),
                new Course("AE23502 - HIGH SPEED AERODYNAMICS", 3),
                new Course("AE23503 - AIRCRAFT STRUCTURES", 4),
                new Course("AE23504 - COMPOSITE MATERIALS AND STRUCTURES", 3),
                new Course("PROFESSIONAL ELECTIVE-2", 3),
                new Course("AE23505 - SUMMER INTERNSHIP", 2),
                new Course("AE23U02 - SUSTAINABILITY FOR AEROSPACE ENGINEERING", 3),
                new Course("AE23L01 - SELF LEARNING COURSE", 1)
            };
        } else if (year == 3 && semester == 2) {    // Semester VI = 27 (+ honours)
            return new Course[] {
                new Course("AE23601 - HYPERSONIC AND ROCKET PROPULSION", 3),
                new Course("PROFESSIONAL ELECTIVE-3", 3),
                new Course("AE23602 - FINITE ELEMENT METHOD", 4),
                new Course("AE23603 - AIRCRAFT STABILITY AND CONTROL", 3),
                new Course("AE23604 - COMPUTATIONAL FLUID DYNAMICS", 4),
                new Course("AE23605 - AEROSPACE VEHICLE DESIGN-I", 2),
                new Course("AE23606 - EXPERIMENTS IN FLIGHT LABORATORY", 2),
                new Course("EMERGING TECHNOLOGY COURSE-1", 3),
                new Course("UC23E01 - ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3)
            };
        } else if (year == 4 && semester == 1) {    // Semester VII = 20 (+ honours)
            return new Course[] {
                new Course("AE23701 - AEROSPACE VEHICLE DESIGN-II", 2),
                new Course("PROFESSIONAL ELECTIVE-4", 3),
                new Course("PROFESSIONAL ELECTIVE-5", 3),
                new Course("PROFESSIONAL ELECTIVE-6", 3),
                new Course("OPEN ELECTIVE-2", 3),
                new Course("INDUSTRY ORIENTED COURSE-3", 1),
                new Course("SKILL DEVELOPMENT COURSE-3", 2),
                new Course("EMERGING TECHNOLOGY COURSE-2", 3)
            };
        } else if (year == 4 && semester == 2) {    // Semester VIII = 8
            return new Course[] {
                new Course("AE23801 - PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursAERO(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (0 = not taking honours, 1 = capstone design project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 0) {
            return new double[] {0.0, 0};
        } else if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "AE23D01 - CAPSTONE DESIGN PROJECT - LEVEL I";
            } else if (year == 3 && semester == 2) {
                courseName = "AE23D02 - CAPSTONE DESIGN PROJECT - LEVEL II";
            } else {
                courseName = "AE23D03 - CAPSTONE DESIGN PROJECT - LEVEL III";
            }

            int credits = getCapstoneCreditsAERO(year, semester);
            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * credits;
            System.out.printf("Honours GPA: %.2f%n", points / credits);
            return new double[] {points, credits};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsAERO(int year, int semester) {
        if (year == 1 && semester == 1) return 23;
        if (year == 1 && semester == 2) return 24;
        if (year == 2 && semester == 1) return 26;
        if (year == 2 && semester == 2) return 27;
        if (year == 3 && semester == 1) return 23;
        if (year == 3 && semester == 2) return 27;
        if (year == 4 && semester == 1) return 20;
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static int getCapstoneCreditsAERO(int year, int semester) {
        if (year == 3 && semester == 1) return 4;
        if (year == 3 && semester == 2) return 6;
        return 8; // year 4, semester 1
    }

private static Course[] getCoursesECE(int year, int semester) {
        if (year == 1 && semester == 1) {
            return new Course[] {
                new Course("EN23C01 - FOUNDATION ENGLISH", 3),
                new Course("MA23C01 - MATRICES AND CALCULUS", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("CS23C04 - PROGRAMMING IN C", 4),
                new Course("ME23C04 - MAKER SPACE", 3),
                new Course("UC23H01 - HERITAGE OF TAMILS", 1)
            };
        } else if (year == 1 && semester == 2) {
            return new Course[] {
                new Course("EN23C02 - PROFESSIONAL COMMUNICATION", 3),
                new Course("MA23C02 - ORDINARY DIFFERENTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("EC23C03 - ELECTRONIC DEVICES", 4),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("EC23C06 - DATA STRUCTURES AND PROGRAMMING IN C++", 4),
                new Course("EC23C04 - CIRCUIT ANALYSIS", 4),
                new Course("UC23H02 - TAMILS AND TECHNOLOGY", 1)
            };
        } else if (year == 2 && semester == 1) {
            return new Course[] {
                new Course("MA23C03 - LINEAR ALGEBRA AND NUMERICAL METHODS", 4),
                new Course("EC23301 - ELECTROMAGNETIC FIELDS", 3),
                new Course("EC23C13 - DIGITAL ELECTRONICS AND SYSTEM DESIGN", 4),
                new Course("EC23302 - SIGNALS AND SYSTEMS", 3),
                new Course("EC23C02 - ANALOG CIRCUITS DESIGN", 4),
                new Course("SKILL DEVELOPMENT COURSE I", 2),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2)
            };
        } else if (year == 2 && semester == 2) {
            return new Course[] {
                new Course("EC23401 - DIGITAL SIGNAL PROCESSING", 4),
                new Course("EC23402 - TRANSMISSION LINES", 3),
                new Course("EC23403 - ANALOG AND BASEBAND COMMUNICATION", 4),
                new Course("EC23C05 - ANALOG ELECTRONIC SYSTEM DESIGN", 4),
                new Course("EC23C10 - COMPUTER ARCHITECTURE AND ORGANIZATION", 3),
                new Course("EC23C11 - CONTROL SYSTEMS", 3),
                new Course("SKILL DEVELOPMENT COURSE II", 2),
                new Course("EC23L01 - SELF LEARNING COURSE", 1)
            };
        } else if (year == 3 && semester == 1) {
            return new Course[] {
                new Course("EC23501 - ANTENNA AND WAVE PROPAGATION", 4),
                new Course("EC23502 - DIGITAL COMMUNICATION", 4),
                new Course("EC23C21 - MICROPROCESSORS AND MICROCONTROLLERS", 4),
                new Course("EC23503 - COMMUNICATION NETWORKS", 4),
                new Course("PROFESSIONAL ELECTIVE I", 3),
                new Course("INDUSTRY ORIENTED COURSE I / SUMMER INTERNSHIP I", 1),
                new Course("UC23E01 - ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3)
            };
        } else if (year == 3 && semester == 2) {
            return new Course[] {
                new Course("EC23C14 - DIGITAL VLSI DESIGN", 4),
                new Course("EC23601 - WIRELESS COMMUNICATIONS", 4),
                new Course("EC23602 - MACHINE LEARNING", 3),
                new Course("EMERGING TECHNOLOGY I", 3),
                new Course("OPEN ELECTIVE I", 3),
                new Course("SKILL DEVELOPMENT COURSE III", 2),
                new Course("INDUSTRY ORIENTED COURSE II", 1),
                new Course("EC23U02 - PERSPECTIVES OF SUSTAINABLE DEVELOPMENT", 3)
            };
        } else if (year == 4 && semester == 1) {
            return new Course[] {
                new Course("EC23701 - MILLIMETER WAVE AND OPTICAL COMMUNICATION", 4),
                new Course("PROFESSIONAL ELECTIVE II", 3),
                new Course("PROFESSIONAL ELECTIVE III", 3),
                new Course("PROFESSIONAL ELECTIVE IV", 3),
                new Course("EMERGING TECHNOLOGY II", 3),
                new Course("OPEN ELECTIVE II", 3),
                new Course("EC23702 - MINI PROJECT", 2),
                new Course("EC23703 - INDUSTRY ORIENTED COURSE III / SUMMER INTERNSHIP II", 1),
                new Course("EC23U01 - STANDARDS - ELECTRONICS AND COMMUNICATION ENGINEERING", 1)
            };
        } else if (year == 4 && semester == 2) {
            return new Course[] {
                new Course("EC23801 - PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursECE(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CS23D01 - CAPSTONE IDEATION";
            } else if (year == 3 && semester == 2) {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 1";
            } else {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 2";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsECE(int year, int semester) {
        if (year == 1 && semester == 1) return 23;
        if (year == 1 && semester == 2) return 24;
        if (year == 2 && semester == 1) return 22;
        if (year == 2 && semester == 2) return 24;
        if (year == 3 && semester == 1) return 23 + 6; // 23 course credits + 6 honours
        if (year == 3 && semester == 2) return 23 + 6; // 23 course credits + 6 honours
        if (year == 4 && semester == 1) return 23 + 6; // 23 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

private static Course[] getCoursesAUTO(int year, int semester) {
        if (year == 1 && semester == 1) {
            return new Course[] {
                new Course("EN23C01 - FOUNDATION ENGLISH", 3),
                new Course("MA23C01 - MATRICES AND CALCULUS", 4),
                new Course("PH23C01 - ENGINEERING PHYSICS", 4),
                new Course("ME23C01 - ENGINEERING DRAWING AND 3D MODELLING", 4),
                new Course("EE23C03 - BASICS OF ELECTRICAL AND ELECTRONICS ENGINEERING", 3),
                new Course("CS23C02 - COMPUTER PROGRAMMING IN PYTHON", 4),
                new Course("UC23H01 - HERITAGE OF TAMILS", 1)
            };
        } else if (year == 1 && semester == 2) {
            return new Course[] {
                new Course("EN23C02 - PROFESSIONAL COMMUNICATION", 3),
                new Course("MA23C02 - ORDINARY DIFFERENTIAL EQUATIONS AND TRANSFORM TECHNIQUES", 4),
                new Course("PH23C04 - FUNDAMENTALS OF MATERIALS SCIENCE AND ENGINEERING", 3),
                new Course("CY23C01 - ENGINEERING CHEMISTRY", 4),
                new Course("ME23C04 - MAKERSPACE", 3),
                new Course("ME23C03 - ENGINEERING MECHANICS", 4),
                new Course("UC23H02 - TAMILS AND TECHNOLOGY", 1)
            };
        } else if (year == 2 && semester == 1) {
            return new Course[] {
                new Course("MA23C06 - PARTIAL DIFFERENTIAL EQUATIONS AND COMPLEX FUNCTIONS", 4),
                new Course("AU23301 - MANUFACTURING PROCESSES AND MACHINE TOOLS", 4),
                new Course("AU23C01 - APPLIED THERMODYNAMICS", 4),
                new Course("AU23C02 - MECHANICS OF SOLIDS", 4),
                new Course("AU23302 - AUTOMOTIVE ENGINES", 4),
                new Course("SKILL DEVELOPMENT COURSE - LEVEL I", 2),
                new Course("UC23U01 - UNIVERSAL HUMAN VALUES", 2)
            };
        } else if (year == 2 && semester == 2) {
            return new Course[] {
                new Course("AU23401 - AUTOMOTIVE CHASSIS", 4),
                new Course("AU23402 - VEHICLE BODY ENGINEERING AND ERGONOMICS", 3),
                new Course("AU23403 - FLUID MECHANICS FOR AUTOMOTIVE APPLICATIONS", 4),
                new Course("AU23404 - KINEMATICS AND DYNAMICS FOR AUTOMOTIVE APPLICATIONS", 3),
                new Course("AU23405 - FUELS, LUBRICANTS AND COOLANTS", 4),
                new Course("AU23406 - AUTOMOTIVE POLLUTION AND CONTROL", 4),
                new Course("SKILL DEVELOPMENT COURSE - LEVEL II", 2)
            };
        } else if (year == 3 && semester == 1) {
            return new Course[] {
                new Course("AU23501 - ENGINEERING DESIGN", 4),
                new Course("AU23502 - AUTOMOTIVE TRANSMISSION", 4),
                new Course("AU23503 - AUTOMOTIVE ELECTRICAL AND ELECTRONICS SYSTEMS", 4),
                new Course("AU23504 - VEHICLE MAINTENANCE AND TESTING", 4),
                new Course("OPEN ELECTIVE - I", 3),
                new Course("INDUSTRY ORIENTED COURSE - I", 1),
                new Course("UC23E01 - ENGINEERING ENTREPRENEURSHIP DEVELOPMENT", 3),
                new Course("AU23U01 - STANDARDS - AUTOMOBILE ENGINEERING", 1),
                new Course("AU23505 - SUMMER INTERNSHIP", 2)
            };
        } else if (year == 3 && semester == 2) {
            return new Course[] {
                new Course("PROFESSIONAL ELECTIVE - I", 3),
                new Course("PROFESSIONAL ELECTIVE - II", 3),
                new Course("PROFESSIONAL ELECTIVE - III", 3),
                new Course("PROFESSIONAL ELECTIVE - IV", 3),
                new Course("PROFESSIONAL ELECTIVE - V", 3),
                new Course("PROFESSIONAL ELECTIVE - VI", 3),
                new Course("OPEN ELECTIVE - II", 3),
                new Course("AU23E01 - EMERGING TECHNOLOGY COURSE - I", 3),
                new Course("INDUSTRY ORIENTED COURSE - II", 1),
                new Course("AU23L01 - SELF-LEARNING COURSE", 1)
            };
        } else if (year == 4 && semester == 1) {
            return new Course[] {
                new Course("AU23701 - DESIGN OF VEHICLE COMPONENTS", 4),
                new Course("AU23702 - ELECTRIC AND HYBRID VEHICLES", 4),
                new Course("AU23703 - VEHICLE DYNAMICS", 4),
                new Course("AU23704 - IC ENGINE PROCESS MODELING", 4),
                new Course("AU23E01 - EMERGING TECHNOLOGY COURSE - II", 3),
                new Course("INDUSTRY ORIENTED COURSE - III / INDUSTRIAL CASE STUDY", 1),
                new Course("AU23U02 - PERSPECTIVES OF SUSTAINABLE DEVELOPMENT IN AUTOMOBILE", 3)
            };
        } else if (year == 4 && semester == 2) {
            return new Course[] {
                new Course("AU23801 - PROJECT WORK / INTERNSHIP CUM PROJECT WORK", 8)
            };
        }
        return null;
    }

private static double[] calculateHonoursAUTO(int year, int semester) {
        if (!(year == 3 || (year == 4 && semester == 1))) {
            return new double[] {0.0, 0};
        }

        System.out.print("\nHONOURS DEGREE\nEnter type (1 = capstone/project, 2 = two electives): ");
        int type = sc.nextInt();

        if (type == 1) {
            String courseName;
            if (year == 3 && semester == 1) {
                courseName = "CS23D01 - CAPSTONE IDEATION";
            } else if (year == 3 && semester == 2) {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 1";
            } else {
                courseName = "CS23D02 - CAPSTONE PROJECT PHASE 2";
            }

            String grade = readGrade(courseName);
            double points = (double) gradePoint(grade) * 6;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else if (type == 2) {
            String first;
            String second;

            if (year == 3 && semester == 1) {
                first = readGrade("Honours Elective-1");
                second = readGrade("Honours Elective-2");
            } else if (year == 3 && semester == 2) {
                first = readGrade("Honours Elective-3");
                second = readGrade("Honours Elective-4");
            } else {
                first = readGrade("Honours Elective-5");
                second = readGrade("Honours Elective-6");
            }

            double points = gradePoint(first) * 3.0 + gradePoint(second) * 3.0;
            System.out.printf("Honours GPA: %.2f%n", points / 6.0);
            return new double[] {points, 6};
        } else {
            System.out.println("Invalid honours degree type. Honours credits were not included.");
            return new double[] {0.0, 0};
        }
    }

private static int getSemesterCreditsAUTO(int year, int semester) {
        if (year == 1 && semester == 1) return 23;
        if (year == 1 && semester == 2) return 22;
        if (year == 2 && semester == 1) return 24;
        if (year == 2 && semester == 2) return 24;
        if (year == 3 && semester == 1) return 26 + 6; // 26 course credits + 6 honours
        if (year == 3 && semester == 2) return 26 + 6; // 26 course credits + 6 honours
        if (year == 4 && semester == 1) return 23 + 6; // 23 course credits + 6 honours
        if (year == 4 && semester == 2) return 8;
        return 0;
    }

    private static Course[] getCourses(String department, int year, int semester) {
        switch (department) {
            case "CSE": return getCoursesCSE(year, semester);
            case "IT": return getCoursesIT(year, semester);
            case "EI": return getCoursesEI(year, semester);
            case "RPT": return getCoursesRPT(year, semester);
            case "PRODUCTION": return getCoursesPRODUCTION(year, semester);
            case "AIDS": return getCoursesAIDS(year, semester);
            case "AERO": return getCoursesAERO(year, semester);
            case "ECE": return getCoursesECE(year, semester);
            case "AUTO": return getCoursesAUTO(year, semester);
            default: return null;
        }
    }

    private static double[] calculateHonours(String department, int year, int semester) {
        switch (department) {
            case "CSE": return calculateHonoursCSE(year, semester);
            case "IT": return calculateHonoursIT(year, semester);
            case "EI": return calculateHonoursEI(year, semester);
            case "RPT": return calculateHonoursRPT(year, semester);
            case "PRODUCTION": return calculateHonoursPRODUCTION(year, semester);
            case "AIDS": return calculateHonoursAIDS(year, semester);
            case "AERO": return calculateHonoursAERO(year, semester);
            case "ECE": return calculateHonoursECE(year, semester);
            case "AUTO": return calculateHonoursAUTO(year, semester);
            default: return new double[] {0.0, 0};
        }
    }

    private static int getSemesterCredits(String department, int year, int semester) {
        switch (department) {
            case "CSE": return getSemesterCreditsCSE(year, semester);
            case "IT": return getSemesterCreditsIT(year, semester);
            case "EI": return getSemesterCreditsEI(year, semester);
            case "RPT": return getSemesterCreditsRPT(year, semester);
            case "PRODUCTION": return getSemesterCreditsPRODUCTION(year, semester);
            case "AIDS": return getSemesterCreditsAIDS(year, semester);
            case "AERO": return getSemesterCreditsAERO(year, semester);
            case "ECE": return getSemesterCreditsECE(year, semester);
            case "AUTO": return getSemesterCreditsAUTO(year, semester);
            default: return 0;
        }
    }


    private static int getCreditsBeforeSemester(String department, int year, int semester) {
        int total = 0;
        for (int y = 1; y <= 4; y++) {
            for (int s = 1; s <= 2; s++) {
                if (y < year || (y == year && s < semester)) {
                    total += getSemesterCredits(department, y, s);
                }
            }
        }
        return total;
    }

    private static void calculateSemester(String department, String regNo, int year, int semester) {
        Course[] courses = getCourses(department, year, semester);
        if (courses == null) {
            System.out.println("Invalid year or semester. Enter year 1-4 and semester 1-2.");
            return;
        }

        System.out.println("\n==============================================");
        System.out.println("MIT CGPA CALCULATOR");
        System.out.println("Register Number: " + regNo);
        System.out.println("Department: " + department);
        System.out.println("Year " + year + ", Semester " + semester);
        System.out.println("==============================================");

        double previousWeightedPoints = 0.0;
        int previousCredits = getCreditsBeforeSemester(department, year, semester);

        if (!(year == 1 && semester == 1)) {
            System.out.print("Enter previous overall CGPA: ");
            double previousCgpa = sc.nextDouble();
            if (previousCgpa < 0 || previousCgpa > 10) {
                System.out.println("Invalid previous CGPA. It must be between 0 and 10.");
                return;
            }
            previousWeightedPoints = previousCgpa * previousCredits;
            System.out.println("Previous completed credits (estimated from curriculum): " + previousCredits);
        }

        int semesterCredits = 0;
        double semesterWeightedPoints = 0.0;
        int backlogs = 0;
        List<GradeEntry> enteredGrades = new ArrayList<>();

        for (Course course : courses) {
            String grade = readGrade(course.name);
            int points = gradePoint(grade);
            enteredGrades.add(new GradeEntry(course.name, grade, course.credits));
            semesterCredits += course.credits;
            semesterWeightedPoints += (double) points * course.credits;
            if (points == 0) backlogs++;
        }

        double[] honours = calculateHonours(department, year, semester);
        semesterWeightedPoints += honours[0];
        semesterCredits += (int) honours[1];

        if (semesterCredits <= 0) {
            System.out.println("Cannot calculate GPA because semester credits are zero.");
            return;
        }

        double gpa = semesterWeightedPoints / semesterCredits;
        int cumulativeCredits = previousCredits + semesterCredits;
        double overallCgpa = (year == 1 && semester == 1)
                ? gpa
                : (previousWeightedPoints + semesterWeightedPoints) / cumulativeCredits;

        System.out.printf("%nRegister Number: %s%n", regNo);
        System.out.printf("Total semester credits: %d%n", semesterCredits);
        System.out.printf("Semester GPA: %.2f%n", gpa);
        System.out.printf("Overall CGPA: %.2f%n", overallCgpa);
        System.out.println("Number of backlogs: " + backlogs);
        saveRecord(regNo, department, year, semester, gpa, overallCgpa, enteredGrades);
    }

    private static void runConsole() {
        sc = new Scanner(System.in);
        System.out.println("==============================================");
        System.out.println("       MIT DEPARTMENT CGPA MANAGEMENT");
        System.out.println("==============================================");
        System.out.println("Grade points: S=10, A+=9, A=8, B+=7, B=6, C=5, RA/U=0");

        initializeDatabase();

        while (true) {
            System.out.println("\\n========== MAIN MENU ==========");
            System.out.println("1. Calculate CGPA");
            System.out.println("2. Statistics");
            System.out.println("3. Exit");
            System.out.print("Choose an option (1-3): ");

            int menuChoice;
            try {
                menuChoice = sc.nextInt();
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter 1, 2, or 3.");
                sc.nextLine();
                continue;
            }

            switch (menuChoice) {
                case 1: {
                    System.out.println("\\nSelect your department:");
                    System.out.println("1. CSE - Computer Science and Engineering");
                    System.out.println("2. IT - Information Technology");
                    System.out.println("3. EI - Electronics and Instrumentation");
                    System.out.println("4. RPT - Rubber and Plastics Technology");
                    System.out.println("5. Production Engineering");
                    System.out.println("6. AI&DS - Artificial Intelligence and Data Science");
                    System.out.println("7. Aeronautical Engineering");
                    System.out.println("8. ECE - Electronics and Communication Engineering");
                    System.out.println("9. Automobile Engineering");
                    System.out.print("Enter department choice (1-9): ");

                    int choice = sc.nextInt();
                    String department;
                    switch (choice) {
                        case 1: department = "CSE"; break;
                        case 2: department = "IT"; break;
                        case 3: department = "EI"; break;
                        case 4: department = "RPT"; break;
                        case 5: department = "PRODUCTION"; break;
                        case 6: department = "AIDS"; break;
                        case 7: department = "AERO"; break;
                        case 8: department = "ECE"; break;
                        case 9: department = "AUTO"; break;
                        default:
                            System.out.println("Invalid department choice.");
                            continue;
                    }

                    System.out.print("Enter your register number: ");
                    String regNo = sc.next().trim();
                    if (regNo.isEmpty()) {
                        System.out.println("Register number cannot be empty.");
                        continue;
                    }

                    System.out.print("Enter your year (1-4): ");
                    int year = sc.nextInt();
                    System.out.print("Enter your semester (1-2): ");
                    int semester = sc.nextInt();
                    calculateSemester(department, regNo, year, semester);
                    break;
                }
                case 2:
                    showStatistics();
                    break;
                case 3:
                    System.out.println("Exiting MIT CGPA Management. Goodbye!");
                    sc.close();
                    return;
                default:
                    System.out.println("Invalid option. Choose 1, 2, or 3.");
            }
        }
    }

    /** Launches the existing CGPA workflow inside a Java Swing desktop window. */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) { }
            new SwingConsole().launch();
        });
    }

    private static final class SwingConsole {
        private final JTextPane output = new JTextPane();
        private final JTextField input = new JTextField();
        private PipedOutputStream userInput;
        private JFrame frame;
        private JPanel root;
        private JPanel header;
        private JPanel bottom;
        private JPanel menuButtons;
        private JPanel inputPanel;
        private JPanel quickActions;
        private final StringBuilder consoleBuffer = new StringBuilder();
        private String activeCourseForGrade = "";
        private final java.util.Map<String, Style> outputStyles = new java.util.HashMap<>();
        private String lastQuickPrompt = "";
        private boolean showGradeChoices = false;
        private JLabel heading;
        private JLabel subtitle;
        private JLabel inputLabel;
        private JButton calculateButton;
        private JButton statisticsButton;
        private JButton exitButton;
        private JButton sendButton;
        private JButton themeButton;
        private boolean darkMode = true;

        private final Color accent = new Color(99, 102, 241);
        private final Color accentHover = new Color(79, 70, 229);
        private final Color green = new Color(16, 185, 129);
        private final Color greenHover = new Color(5, 150, 105);
        private final Color red = new Color(239, 68, 68);
        private final Color redHover = new Color(220, 38, 38);

        void launch() {
            frame = new JFrame("MIT CGPA Management System");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setMinimumSize(new Dimension(760, 540));
            frame.setSize(980, 700);
            frame.setLocationRelativeTo(null);

            root = new JPanel(new BorderLayout(0, 16));
            root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

            header = new JPanel(new BorderLayout(12, 5));
            header.setBorder(BorderFactory.createEmptyBorder(4, 4, 10, 4));
            JPanel titlePanel = new JPanel(new BorderLayout(0, 5));
            titlePanel.setOpaque(false);
            heading = new JLabel("MIT CGPA MANAGEMENT SYSTEM");
            heading.setFont(new Font("Segoe UI", Font.BOLD, 26));
            subtitle = new JLabel("Academic performance • Semester GPA • Student statistics");
            subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            titlePanel.add(heading, BorderLayout.NORTH);
            titlePanel.add(subtitle, BorderLayout.SOUTH);
            themeButton = new JButton("☀  Light mode");
            styleButton(themeButton, new Color(71, 85, 105), new Color(51, 65, 85));
            themeButton.addActionListener(e -> {
                darkMode = !darkMode;
                themeButton.setText(darkMode ? "☀  Light mode" : "☾  Dark mode");
                applyTheme();
            });
            header.add(titlePanel, BorderLayout.CENTER);
            header.add(themeButton, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);

            output.setEditable(false);
            output.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            output.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            output.setCaretColor(new Color(99, 102, 241));
            outputStyles.put("body", output.addStyle("body", null));
            outputStyles.put("heading", output.addStyle("heading", null));
            outputStyles.put("success", output.addStyle("success", null));
            outputStyles.put("muted", output.addStyle("muted", null));
            StyleConstants.setFontFamily(outputStyles.get("body"), "Segoe UI");
            StyleConstants.setFontSize(outputStyles.get("body"), 16);
            StyleConstants.setFontFamily(outputStyles.get("heading"), "Segoe UI");
            StyleConstants.setBold(outputStyles.get("heading"), true);
            StyleConstants.setFontSize(outputStyles.get("heading"), 18);
            StyleConstants.setFontFamily(outputStyles.get("success"), "Segoe UI");
            StyleConstants.setBold(outputStyles.get("success"), true);
            StyleConstants.setFontSize(outputStyles.get("success"), 16);
            StyleConstants.setFontFamily(outputStyles.get("muted"), "Segoe UI");
            StyleConstants.setFontSize(outputStyles.get("muted"), 14);
            JScrollPane scrollPane = new JScrollPane(output);
            scrollPane.setBorder(BorderFactory.createLineBorder(new Color(148, 163, 184, 90), 1));
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            root.add(scrollPane, BorderLayout.CENTER);

            bottom = new JPanel(new BorderLayout(0, 12));
            menuButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            calculateButton = new JButton("🧮  Calculate CGPA");
            statisticsButton = new JButton("📊  Statistics");
            exitButton = new JButton("🚪  Exit");
            styleButton(calculateButton, accent, accentHover);
            styleButton(statisticsButton, green, greenHover);
            styleButton(exitButton, red, redHover);
            calculateButton.addActionListener(e -> sendLine("1"));
            statisticsButton.addActionListener(e -> sendLine("2"));
            exitButton.addActionListener(e -> sendLine("3"));
            menuButtons.add(calculateButton);
            menuButtons.add(statisticsButton);
            menuButtons.add(exitButton);
            bottom.add(menuButtons, BorderLayout.NORTH);

            quickActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            quickActions.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            quickActions.setVisible(false);
            bottom.add(quickActions, BorderLayout.CENTER);

            inputPanel = new JPanel(new BorderLayout(10, 0));
            inputPanel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
            inputLabel = new JLabel("✍️  Enter choice / value");
            inputLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
            input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            input.setPreferredSize(new Dimension(100, 42));
            input.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(148, 163, 184, 150), 1),
                    BorderFactory.createEmptyBorder(8, 11, 8, 11)));
            inputPanel.add(inputLabel, BorderLayout.WEST);
            inputPanel.add(input, BorderLayout.CENTER);
            sendButton = new JButton("📩  Submit");
            styleButton(sendButton, accent, accentHover);
            sendButton.setPreferredSize(new Dimension(125, 42));
            sendButton.addActionListener(e -> submitInput());
            input.addActionListener(e -> submitInput());
            inputPanel.add(sendButton, BorderLayout.EAST);
            inputPanel.setVisible(false);
            bottom.add(inputPanel, BorderLayout.SOUTH);
            root.add(bottom, BorderLayout.SOUTH);

            frame.setContentPane(root);
            applyTheme();
            frame.setVisible(true);
            startConsoleWorker();
            input.requestFocusInWindow();
        }

        private String emojiFontFamily() {
            java.util.Set<String> available = new java.util.HashSet<>(
                    java.util.Arrays.asList(java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getAvailableFontFamilyNames()));
            String[] preferred = {"Segoe UI Emoji", "Noto Color Emoji", "Noto Emoji", "Apple Color Emoji", "Symbola", "Dialog"};
            for (String family : preferred) if (available.contains(family)) return family;
            return "Dialog";
        }

        private void styleButton(JButton button, Color normal, Color hover) {
            button.setFont(new Font(emojiFontFamily(), Font.BOLD, 13));
            button.setForeground(Color.WHITE);
            button.setBackground(normal);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            button.setFocusPainted(false);
            button.setBorderPainted(false);
            button.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                    button.setBackground(hover);
                }
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    button.setBackground(normal);
                }
            });
        }

        private JButton quickButton(String text, Color normal, Color hover, Runnable action) {
            JButton button = new JButton(text);
            styleButton(button, normal, hover);
            button.setFont(new Font("Segoe UI", Font.BOLD, 14));
            button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
            button.addActionListener(e -> action.run());
            return button;
        }

        private void showQuickActions(String promptKey, String courseName, String mode) {
            if (promptKey.equals(lastQuickPrompt)) return;
            lastQuickPrompt = promptKey;
            quickActions.removeAll();
            quickActions.setVisible(true);
            inputPanel.setVisible(false);
            Color blue = new Color(59, 130, 246);
            Color blueHover = new Color(37, 99, 235);
            Color teal = new Color(20, 184, 166);
            Color tealHover = new Color(13, 148, 136);
            Color purple = new Color(139, 92, 246);
            Color purpleHover = new Color(124, 58, 237);

            if (mode.equals("year")) {
                quickActions.add(styledPromptLabel("📅  Choose academic year"));
                for (int i = 1; i <= 4; i++) {
                    final int year = i;
                    quickActions.add(quickButton("📘  Year " + i, blue, blueHover, () -> {
                        submitQuickChoice(String.valueOf(year));
                        clearQuickActions();
                    }));
                }
            } else if (mode.equals("semester")) {
                quickActions.add(styledPromptLabel("🗓️  Choose semester"));
                for (int i = 1; i <= 2; i++) {
                    final int semester = i;
                    quickActions.add(quickButton("📖  Semester " + i, teal, tealHover, () -> {
                        submitQuickChoice(String.valueOf(semester));
                        clearQuickActions();
                    }));
                }
            } else if (mode.equals("comparison")) {
                quickActions.add(styledPromptLabel("📊  What do you want to compare?"));
                String[] choices = {"Between departments", "Between academic years", "Between semesters", "Between subjects/courses"};
                for (int i = 0; i < choices.length; i++) {
                    final int choice = i + 1;
                    quickActions.add(quickButton((choice) + ".  " + choices[i], blue, blueHover, () -> {
                        submitQuickChoice(String.valueOf(choice));
                        clearQuickActions();
                    }));
                }
            } else if (mode.equals("graph")) {
                quickActions.add(styledPromptLabel("📈  Choose graph type"));
                String[] choices = {"Passed vs. failed", "Total students", "All statistics"};
                Color[] colors = {teal, blue, purple};
                Color[] hovers = {tealHover, blueHover, purpleHover};
                for (int i = 0; i < choices.length; i++) {
                    final int choice = i + 1;
                    quickActions.add(quickButton(choice + ".  " + choices[i], colors[i], hovers[i], () -> {
                        submitQuickChoice(String.valueOf(choice));
                        clearQuickActions();
                    }));
                }
            } else if (mode.equals("department")) {
                quickActions.add(styledPromptLabel("🏛️  Choose department"));
                String[] names = {"CSE", "IT", "EI", "RPT", "Production", "AI&DS", "Aero", "ECE", "Auto"};
                for (int i = 0; i < names.length; i++) {
                    final int choice = i + 1;
                    quickActions.add(quickButton((i % 2 == 0 ? "●  " : "◆  ") + names[i], i % 2 == 0 ? blue : purple,
                            i % 2 == 0 ? blueHover : purpleHover, () -> {
                                submitQuickChoice(String.valueOf(choice));
                                clearQuickActions();
                            }));
                }
            } else if (mode.equals("grade")) {
                activeCourseForGrade = courseName;
                JButton courseButton = quickButton("▣  " + courseName + "   |   Select grade  ▾", purple, purpleHover, () -> {
                    showGradeChoices = true;
                    quickActions.removeAll();
                    quickActions.add(styledPromptLabel("Select a grade"));
                    String[] grades = {"S", "A+", "A", "B+", "B", "C", "RA"};
                    Color[] colors = {new Color(16, 185, 129), new Color(14, 165, 233), new Color(59, 130, 246),
                            new Color(99, 102, 241), new Color(139, 92, 246), new Color(217, 119, 6), new Color(239, 68, 68)};
                    for (int i = 0; i < grades.length; i++) {
                        final String grade = grades[i];
                        Color c = colors[i];
                        quickActions.add(quickButton(grade, c, c.darker(), () -> {
                            appendGradeSelection(activeCourseForGrade, grade);
                            sendLine(grade);
                            showGradeChoices = false;
                            clearQuickActions();
                        }));
                    }
                    quickActions.revalidate();
                    quickActions.repaint();
                });
                quickActions.add(courseButton);
            }
            quickActions.revalidate();
            quickActions.repaint();
        }

        private JLabel styledPromptLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(new Font(emojiFontFamily(), Font.BOLD, 13));
            label.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            label.setForeground(darkMode ? new Color(226, 232, 240) : new Color(51, 65, 85));
            return label;
        }

        // Reflect button selections in the transcript just like typed input.
        private void submitQuickChoice(String value) {
            sendLine(value);
        }

        private void appendGradeSelection(String course, String grade) {
            String clean = course == null ? "Current course" : course.trim();
            String display = clean.contains(" - ") ? clean : clean + " - Course";
            appendStyledLine("✓  " + display + " :  " + grade + "\n", "success");
        }

        private void appendStyledLine(String text, String styleName) {
            SwingUtilities.invokeLater(() -> {
                try {
                    StyledDocument doc = output.getStyledDocument();
                    doc.insertString(doc.getLength(), text, outputStyles.get(styleName));
                    output.setCaretPosition(doc.getLength());
                } catch (javax.swing.text.BadLocationException ignored) { }
            });
        }

        private void clearQuickActions() {
            showGradeChoices = false;
            quickActions.removeAll();
            quickActions.setVisible(false);
            quickActions.revalidate();
            quickActions.repaint();
        }

        private void detectPromptButtons() {
            String text;
            synchronized (consoleBuffer) {
                text = consoleBuffer.toString();
            }
            if (text.isEmpty()) return;

            String lower = text.toLowerCase(Locale.ROOT);
            int lastEnterAt = lower.lastIndexOf("enter ");
            int lastDepartmentAt = lower.lastIndexOf("department choice (1-9):");
            int lastMenuAt = lower.lastIndexOf("choose an option (1-3):");
            int lastComparisonAt = lower.lastIndexOf("choose comparison level (1-4):");
            int lastGraphAt = lower.lastIndexOf("graph choice (1-3):");
            int lastPromptAt = Math.max(Math.max(lastEnterAt, lastDepartmentAt),
                    Math.max(Math.max(lastMenuAt, lastComparisonAt), lastGraphAt));
            String currentPrompt = lastPromptAt >= 0 ? lower.substring(lastPromptAt) : "";
            int gradeAt = lower.lastIndexOf("enter your grade");

            // Only allow the main menu buttons to submit choices while the main menu is active.
            // Otherwise clicking Statistics again could accidentally submit "2" as a year/semester.
            boolean mainMenuActive = currentPrompt.startsWith("choose an option (1-3):");
            SwingUtilities.invokeLater(() -> {
                calculateButton.setEnabled(mainMenuActive);
                statisticsButton.setEnabled(mainMenuActive);
                exitButton.setEnabled(mainMenuActive);
            });

            if (currentPrompt.startsWith("enter your grade")) {
                if (showGradeChoices) return;
                int lineEnd = text.lastIndexOf("\n", gradeAt - 1);
                int courseStart = lineEnd > 0 ? text.lastIndexOf("\n", lineEnd - 1) : -1;
                String course = text.substring(courseStart + 1, lineEnd >= 0 ? lineEnd : gradeAt).trim();
                if (course.isEmpty() || course.length() > 140) course = "Current course";
                String finalCourse = course;
                activeCourseForGrade = finalCourse;
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("grade:" + finalCourse + ":" + gradeAt, finalCourse, "grade");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else if (currentPrompt.startsWith("enter your year (1-4):") ||
                    currentPrompt.startsWith("enter year (1-4):")) {
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("year:" + lastPromptAt, "", "year");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else if (currentPrompt.startsWith("enter your semester (1-2):") ||
                    currentPrompt.startsWith("enter semester (1-2):")) {
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("semester:" + lastPromptAt, "", "semester");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else if (currentPrompt.startsWith("department choice (1-9):") || currentPrompt.startsWith("enter department choice (1-9):")) {
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("department:" + lastPromptAt, "", "department");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else if (currentPrompt.startsWith("choose comparison level (1-4):")) {
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("comparison:" + lastPromptAt, "", "comparison");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else if (currentPrompt.startsWith("graph choice (1-3):")) {
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(false);
                    showQuickActions("graph:" + lastPromptAt, "", "graph");
                    bottom.revalidate();
                    bottom.repaint();
                });
            } else {
                String latest = text.substring(Math.max(0, text.length() - 260)).toLowerCase(Locale.ROOT);
                boolean manualPrompt = currentPrompt.startsWith("enter your register number") ||
                        currentPrompt.startsWith("enter register number") ||
                        currentPrompt.startsWith("register number:") ||
                        (currentPrompt.startsWith("enter ") && (currentPrompt.contains(":") || currentPrompt.contains("?")) &&
                                !currentPrompt.startsWith("enter your grade"));
                boolean menuPrompt = latest.contains("choose an option (1-3):");
                boolean showManualInput = manualPrompt && !menuPrompt;
                SwingUtilities.invokeLater(() -> {
                    inputPanel.setVisible(showManualInput);
                    if (showManualInput) inputLabel.setText("Enter register number / value:");
                    bottom.revalidate();
                    bottom.repaint();
                });
            }
        }

        private void applyTheme() {
            Color background = darkMode ? new Color(15, 23, 42) : new Color(245, 247, 251);
            Color panelBackground = darkMode ? new Color(30, 41, 59) : Color.WHITE;
            Color foreground = darkMode ? new Color(226, 232, 240) : new Color(30, 41, 59);
            Color muted = darkMode ? new Color(148, 163, 184) : new Color(100, 116, 139);
            Color fieldBackground = darkMode ? new Color(15, 23, 42) : new Color(248, 250, 252);

            root.setBackground(background);
            header.setBackground(background);
            bottom.setBackground(background);
            menuButtons.setBackground(background);
            inputPanel.setBackground(background);
            heading.setForeground(foreground);
            subtitle.setForeground(muted);
            inputLabel.setForeground(foreground);
            output.setBackground(darkMode ? new Color(17, 24, 39) : new Color(255, 255, 255));
            output.setForeground(foreground);
            output.setCaretColor(accent);
            StyleConstants.setForeground(outputStyles.get("body"), foreground);
            StyleConstants.setForeground(outputStyles.get("heading"), darkMode ? new Color(165, 180, 252) : new Color(67, 56, 202));
            StyleConstants.setForeground(outputStyles.get("success"), darkMode ? new Color(110, 231, 183) : new Color(5, 150, 105));
            StyleConstants.setForeground(outputStyles.get("muted"), muted);
            input.setBackground(fieldBackground);
            input.setForeground(foreground);
            input.setCaretColor(foreground);
            input.setSelectionColor(accent);
            input.setSelectedTextColor(Color.WHITE);
            frame.getContentPane().setBackground(background);
            frame.repaint();
        }

        private void startConsoleWorker() {
            try {
                PipedInputStream consoleInput = new PipedInputStream(8192);
                userInput = new PipedOutputStream(consoleInput);
                System.setIn(consoleInput);
                PrintStream guiOutput = new PrintStream(new TextAreaOutputStream(), true);
                System.setOut(guiOutput);
                System.setErr(guiOutput);
                Thread worker = new Thread(MITCGPACalculator_3::runConsole, "cgpa-console-worker");
                worker.setDaemon(true);
                worker.start();
            } catch (IOException e) {
                output.setText("Could not start the application: " + e.getMessage());
            }
        }

        private void submitInput() {
            String value = input.getText();
            if (value == null || value.trim().isEmpty()) return;
            appendOutput("> " + value.trim() + "\n");
            input.setText("");
            clearQuickActions();
            sendLine(value.trim());
        }

        private void sendLine(String value) {
            if (userInput == null) return;
            try {
                userInput.write((value + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                userInput.flush();
            } catch (IOException e) {
                appendOutput("Application input is closed. Restart the program to use it again.\n");
            }
        }

        private void appendOutput(String text) {
            synchronized (consoleBuffer) {
                consoleBuffer.append(text);
                if (consoleBuffer.length() > 12000) consoleBuffer.delete(0, consoleBuffer.length() - 9000);
            }
            String display = text;
            // Hide the console-style grade prompt; grade choices live in the button tray.
            // Remove the course-name/prompt pair together so it can be shown as a grade card later.
            display = display.replaceAll("(?s)([^\\n]+)\\n(?i:ENTER YOUR GRADE[^\\n]*)", "");
            display = display.replaceAll("(?i)ENTER YOUR GRADE[^\\n]*", "");
            display = display.replace("\\n========== MAIN MENU ==========", "========== MAIN MENU ==========");
            display = display.replaceAll("(?m)^\\s*\\n(?=\\s*========== MAIN MENU ==========)", "");
            if (!display.isEmpty()) {
                final String rendered = display;
                SwingUtilities.invokeLater(() -> {
                    try {
                        StyledDocument doc = output.getStyledDocument();
                        String[] lines = rendered.split("(?<=\n)", -1);
                        for (String line : lines) {
                            if (line.isEmpty()) continue;
                            String trimmed = line.trim();
                            String style = "body";
                            if (trimmed.toUpperCase(Locale.ROOT).contains("STUDENT STATISTICS") ||
                                    trimmed.equalsIgnoreCase("MIT DEPARTMENT CGPA MANAGEMENT") ||
                                    trimmed.matches("=+\\s*MAIN MENU\\s*=+") ||
                                    trimmed.startsWith("==========")) style = "heading";
                            else if (trimmed.toLowerCase(Locale.ROOT).contains("saved to database") ||
                                    trimmed.toLowerCase(Locale.ROOT).contains("successfully")) style = "success";
                            else if (trimmed.startsWith("---") || trimmed.startsWith("====")) style = "muted";
                            doc.insertString(doc.getLength(), line, outputStyles.get(style));
                        }
                        output.setCaretPosition(doc.getLength());
                    } catch (javax.swing.text.BadLocationException ignored) { }
                });
            }
            detectPromptButtons();
        }

        private final class TextAreaOutputStream extends OutputStream {
            @Override
            public void write(int value) {
                if (value != '\r') appendOutput(String.valueOf((char) value));
            }

            @Override
            public void write(byte[] bytes, int offset, int length) {
                String text = new String(bytes, offset, length, java.nio.charset.StandardCharsets.UTF_8)
                        .replace("\r", "");
                if (!text.isEmpty()) appendOutput(text);
            }
        }
    }
}
