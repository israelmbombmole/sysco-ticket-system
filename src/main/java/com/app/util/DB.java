package com.app.util;

import com.app.dao.DirectionDAO;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

public class DB {

    static {
        DbConfig.load();
    }

    public static Connection getConnection() throws SQLException {
        if (DbConfig.isOracle()) {
            try {
                Class.forName("oracle.jdbc.OracleDriver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("Oracle JDBC driver not on classpath", e);
            }
            String user = DbConfig.getOracleUser();
            if (user == null || user.isEmpty()) {
                throw new SQLException("Set oracle.user (and oracle.password) in db.properties");
            }
            Connection raw = DriverManager.getConnection(
                    DbConfig.getOracleUrl(), user, DbConfig.getOraclePassword());
            return SqlDialect.wrapForOracleRewriting(raw);
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException(e);
        }
        Connection conn = DriverManager.getConnection(DbConfig.getSqliteJdbcUrl());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON;");
            st.execute("PRAGMA busy_timeout = 10000;");
            st.execute("PRAGMA journal_mode = WAL;");
            st.execute("PRAGMA timezone = 'localtime';");
        }
        return conn;
    }

    private static void initOracle() {
        try (Connection c = getConnection()) {
            OracleBootstrap.install(c);
            com.app.dao.DirectionDAO.applyStripParentheticalsAndMergeDuplicates(c);
            System.out.println("Oracle schema + seed completed.");
        } catch (Exception e) {
            throw new RuntimeException("Oracle database initialization failed", e);
        }
    }

    /** Called from {@link OracleBootstrap} after core seed. */
    static void seedDirectionSousDirectionMappingsOracle(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM sous_directions WHERE name='AUTRE'")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                seedDirectionSousDirectionMappings(c, rs.getInt(1));
            }
        }
    }

    public static void init() {
        if (DbConfig.isOracle()) {
            initOracle();
            return;
        }

        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            System.out.println("USING DB FILE: " + DbConfig.getSqliteJdbcUrl());

            // =====================================================
            // SOUS DIRECTIONS
            // =====================================================
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sous_directions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE
                    )
                    """);

            st.executeUpdate("INSERT OR IGNORE INTO sous_directions(name) VALUES ('AUTRE')");

            // =====================================================
            // DIRECTIONS
            // =====================================================
            st.execute("""
                    CREATE TABLE IF NOT EXISTS directions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE,
                        sous_direction_id INTEGER,
                        FOREIGN KEY(sous_direction_id) REFERENCES sous_directions(id)
                    )
                    """);

            st.execute("""
                    CREATE TABLE IF NOT EXISTS direction_sous_direction_map (
                        direction_id INTEGER NOT NULL,
                        sous_direction_id INTEGER NOT NULL,
                        PRIMARY KEY(direction_id, sous_direction_id),
                        FOREIGN KEY(direction_id) REFERENCES directions(id) ON DELETE CASCADE,
                        FOREIGN KEY(sous_direction_id) REFERENCES sous_directions(id) ON DELETE CASCADE
                    )
                    """);

            ResultSet rsAutre = st.executeQuery(
                    "SELECT id FROM sous_directions WHERE name='AUTRE'"
            );

            if (rsAutre.next()) {

                int autreId = rsAutre.getInt("id");

                String insertDirectionSQL =
                        "INSERT OR IGNORE INTO directions(name, sous_direction_id) VALUES (?, ?)";

                PreparedStatement psDir = c.prepareStatement(insertDirectionSQL);

                String[] directionNames = {
    "Bureau de Coordination",
    "Direction des Ressources Humaines",
    "Direction de la Réglementation et de la Facilitation",
    "Direction de la Lutte contre la Fraude",
    "Direction du Tarif et des Règles d’Origine",
    "Direction des Affaires Juridiques et Contentieuses",
    "Direction de la Valeur",
    "Direction des Huiles Minérales",
    "Direction des Autres Produits d’Accises",
    "Direction des Recettes du Trésor",
    "Direction des Finances Internes",
    "Direction des Équipements et Logistique",
    "Direction des Statistiques, Documentation et Études Économiques",
    DirectionDAO.CANONICAL_DIRECTION_SYSTEMES_TI,
    "Direction de l’Audit Interne",
    "Direction des Réformes et de la Modernisation"
};

                for (String name : directionNames) {
                    psDir.setString(1, name);
                    psDir.setInt(2, autreId);
                    psDir.executeUpdate();
                }

                seedDirectionSousDirectionMappings(c, autreId);
            }

            // =====================================================
            // USERS
            // =====================================================
            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE NOT NULL,
                        matricule TEXT,
                        password_hash TEXT,
                        role TEXT NOT NULL,
                        active INTEGER DEFAULT 1,
                        sous_direction_id INTEGER,
                        direction_id INTEGER,
                        FOREIGN KEY(sous_direction_id) REFERENCES sous_directions(id),
                        FOREIGN KEY(direction_id) REFERENCES directions(id)
                    )
                    """);
            
           try {

    ResultSet rs = st.executeQuery("PRAGMA table_info(users)");

    boolean columnExists = false;

    while (rs.next()) {
        if ("hidden".equalsIgnoreCase(rs.getString("name"))) {
            columnExists = true;
            break;
        }
    }

    if (!columnExists) {
        st.execute("ALTER TABLE users ADD COLUMN hidden INTEGER DEFAULT 0");
        System.out.println("hidden column added");
    } else {
        System.out.println("ℹ hidden column already exists");
    }

} catch (Exception e) {
    e.printStackTrace(); // 🔥 DO NOT IGNORE
}
            
try { st.execute("ALTER TABLE users ADD COLUMN must_change_password INTEGER DEFAULT 1"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE users ADD COLUMN failed_attempts INTEGER DEFAULT 0"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE users ADD COLUMN lock_until DATETIME"); } catch(Exception ignored){}  
try { st.execute("ALTER TABLE users ADD COLUMN matricule TEXT"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE users ADD COLUMN password_otp_hash TEXT"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE users ADD COLUMN password_otp_expires_at TEXT"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE users ADD COLUMN attendance_signature TEXT"); } catch(Exception ignored){}
try {
    st.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_users_matricule
        ON users(matricule)
        WHERE matricule IS NOT NULL AND TRIM(matricule) != ''
    """);
} catch(Exception ignored){}

// =====================================================
// DEFAULT ADMINS (SAFE + ALWAYS AVAILABLE)
// =====================================================

try {

    String[][] defaultAdmins = {
        {"admin", "admin123"},
        {"admin2", "admin123"},
        {"admin3", "admin123"}
    };

    for (String[] adminData : defaultAdmins) {

        String username = adminData[0];
        String password = adminData[1];

        // check if exists
        PreparedStatement check = c.prepareStatement(
            "SELECT id FROM users WHERE username = ?"
        );
        check.setString(1, username);

        ResultSet rsAdmin = check.executeQuery();

        if (!rsAdmin.next()) {

            // CREATE ADMIN
            PreparedStatement ps = c.prepareStatement("""
                INSERT INTO users(username, password_hash, role, active, failed_attempts, must_change_password)
                VALUES (?, ?, 'ADMIN', 1, 0, 0)
            """);

            ps.setString(1, username);
            ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));

            ps.executeUpdate();

            System.out.println("✅ Created default admin: " + username);

        } else {

            // 🔥 FORCE RESET (VERY IMPORTANT)
            PreparedStatement update = c.prepareStatement("""
                UPDATE users
                SET password_hash = ?,
                    active = 1,
                    failed_attempts = 0
                WHERE username = ?
            """);

            update.setString(1, BCrypt.hashpw(password, BCrypt.gensalt()));
            update.setString(2, username);

            update.executeUpdate();

            System.out.println("🔄 Reset admin: " + username);
        }
    }

} catch (Exception e) {
    e.printStackTrace();
}

try {
    AttendanceSignatureUtil.backfillMissing(c);
} catch (Exception e) {
    e.printStackTrace();
}

      


// 1. CREATE TABLE FIRST
st.execute("""
    CREATE TABLE IF NOT EXISTS departments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL UNIQUE,
        escalation_level1 INTEGER,
        escalation_level2 INTEGER
    )
""");

// 2. INSERT DATA
st.execute("""
    INSERT OR IGNORE INTO departments(name) VALUES
    ('Bureau de Coordination'),
    ('Direction des Ressources Humaines'),
    ('Direction de Réglementation et Facilitation'),
    ('Direction de la Lutte contre la Fraude'),
    ('Direction du Tarif et Règles d’origine'),
    ('Direction des Affaires Juridiques et Contentieuses'),
    ('Direction de la Valeur'),
    ('Direction des Huiles Minérales'),
    ('Direction des Autres Produits d’Accises'),
    ('Direction des Recettes du Trésor'),
    ('Direction des Finances Internes'),
    ('Direction des Equipements et Logistique'),
    ('Direction des statistiques, Documentation et Etudes Economiques'),
    ('Direction des Systèmes et Technologies d\u2019information'),
    ('Direction de l’Audit Interne'),
    ('Direction de Réformes et Modernisation')
""");

// 3. THEN UPDATE
st.execute("""
    UPDATE departments
    SET escalation_level1 = NULL
    WHERE escalation_level1 = 0
""");

st.execute("""
    UPDATE departments
    SET escalation_level2 = NULL
    WHERE escalation_level2 = 0
""");


st.execute("""
           CREATE TABLE IF NOT EXISTS task_events (
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               task_id INTEGER NOT NULL,
               username TEXT,
               type TEXT,
               description TEXT,
               created_at TEXT DEFAULT CURRENT_TIMESTAMP
           )
           
           """);


st.execute("""
           CREATE TABLE IF NOT EXISTS task_assignments (
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               task_id INTEGER,
               user_id INTEGER
           )
           
           """);

try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN due_date TEXT"); }     catch (Exception ignored) {}


st.execute("""
           CREATE TABLE IF NOT EXISTS ticket_tasks (
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               ticket_id INTEGER NOT NULL,
               parent_assignment_id INTEGER,
               title TEXT NOT NULL,
               description TEXT,
               assigned_to INTEGER NOT NULL,
               assigned_by INTEGER NOT NULL,
               status TEXT NOT NULL DEFAULT 'PENDING',
               created_at DATETIME DEFAULT (DATETIME('now','localtime')),
               started_at DATETIME,
               completed_at DATETIME,
           
               FOREIGN KEY(ticket_id) REFERENCES tickets(id),
               FOREIGN KEY(parent_assignment_id) REFERENCES ticket_assignments(id),
               FOREIGN KEY(assigned_to) REFERENCES users(id),
               FOREIGN KEY(assigned_by) REFERENCES users(id)
           )
           
           """);
try {
    st.execute("ALTER TABLE ticket_tasks ADD COLUMN parent_task_id INTEGER");
} catch (Exception ignored) {}



st.execute("""
    CREATE TABLE IF NOT EXISTS user_permissions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER,
        permission TEXT,
        FOREIGN KEY(user_id) REFERENCES users(id)
    )
""");

            st.execute("""
                    CREATE TABLE IF NOT EXISTS user_absences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        user_id INTEGER NOT NULL,
                        start_date TEXT NOT NULL,
                        end_date TEXT NOT NULL,
                        absence_type TEXT NOT NULL,
                        notes TEXT,
                        created_by INTEGER,
                        created_at TEXT DEFAULT (datetime('now','localtime')),
                        FOREIGN KEY(user_id) REFERENCES users(id),
                        FOREIGN KEY(created_by) REFERENCES users(id)
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS ix_user_absences_user ON user_absences(user_id)");
            st.execute("CREATE INDEX IF NOT EXISTS ix_user_absences_dates ON user_absences(start_date, end_date)");

            try (ResultSet rsUaMission =
                         c.getMetaData().getColumns(null, null, "user_absences", "mission_id")) {
                if (!rsUaMission.next()) {
                    st.execute("ALTER TABLE user_absences ADD COLUMN mission_id INTEGER");
                }
            }





            // =====================================================
            // TICKETS
            // =====================================================
            st.execute("""
    CREATE TABLE IF NOT EXISTS tickets (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        title TEXT,
        description TEXT,
        priority TEXT,
        status TEXT,
        ticket_type TEXT NOT NULL,

        department_id INTEGER,
        assigned_to INTEGER,

        created_by INTEGER,
        updated_by INTEGER,

        created_at DATETIME DEFAULT (DATETIME('now','localtime')),
        updated_at DATETIME DEFAULT (DATETIME('now','localtime')),

        started_at DATETIME ,
        closed_at DATETIME ,

        resolution_minutes INTEGER,
        sla_hours INTEGER DEFAULT 24,

        merged_into INTEGER,
        merge_note TEXT,

        FOREIGN KEY(department_id) REFERENCES departments(id),
        FOREIGN KEY(assigned_to) REFERENCES users(id),
        FOREIGN KEY(created_by) REFERENCES users(id),
        FOREIGN KEY(updated_by) REFERENCES users(id)
    )
""");

try { st.execute("ALTER TABLE tickets ADD COLUMN updated_by INTEGER"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE tickets ADD COLUMN updated_at DATETIME"); } catch(Exception ignored){}
try { st.execute("ALTER TABLE tickets ADD COLUMN merged_into INTEGER"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN merge_note TEXT"); }     catch (Exception ignored) {}
try (Statement stmt = c.createStatement()) {stmt.executeUpdate("ALTER TABLE tickets ADD COLUMN ticket_number TEXT");} catch (SQLException ignored) {}


try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN started_at TEXT"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN closed_at TEXT"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN resolution_minutes INTEGER"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN duration_minutes INTEGER"); } catch(Exception ignored){}

try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN description TEXT"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN attachment_path TEXT"); }  catch(Exception ignored){}
try { st.execute("ALTER TABLE ticket_tasks ADD COLUMN created_by INTEGER"); }  catch(Exception ignored){}




st.execute("""
    CREATE TABLE IF NOT EXISTS ticket_attachments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ticket_id INTEGER NOT NULL,
        file_name TEXT,
        file_path TEXT,
        file_type TEXT,
        uploaded_at DATETIME DEFAULT (DATETIME('now','localtime')),
        FOREIGN KEY(ticket_id) REFERENCES tickets(id) ON DELETE CASCADE
    )
""");


try {

    st.execute("""
        ALTER TABLE ticket_attachments
        ADD COLUMN uploaded_by INTEGER
    """);

} catch (SQLException ignored) {
    // column already exists
}





            

st.execute("""
    CREATE TABLE IF NOT EXISTS system_audit(
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        username TEXT,
        action TEXT NOT NULL,
        entity TEXT,
        entity_id INTEGER,
        details TEXT,
        created_at DATETIME DEFAULT (DATETIME('now','localtime'))
    );
""");


// ======================
// TICKET ASSIGNMENTS
// ======================
// ======================
// TICKET ASSIGNMENTS (FIXED CLEAN VERSION)
// ======================
st.execute("""
    CREATE TABLE IF NOT EXISTS ticket_assignments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ticket_id INTEGER NOT NULL,
        agent_id INTEGER NOT NULL,
        assigned_to INTEGER,
        assigned_by INTEGER,
        assigned_at DATETIME DEFAULT (DATETIME('now','localtime')),

        status TEXT DEFAULT 'ASSIGNED',

        started_at DATETIME,
        closed_at DATETIME,
        duration_minutes INTEGER,

        active INTEGER DEFAULT 1,

        from_role TEXT,
        to_role TEXT,
        assignment_type TEXT DEFAULT 'SINGLE', -- 🔥 IMPORTANT

        FOREIGN KEY(ticket_id) REFERENCES tickets(id),
        FOREIGN KEY(agent_id) REFERENCES users(id),
        FOREIGN KEY(assigned_by) REFERENCES users(id)
    );
""");

// ======================
// 🔥 FIX: UNIQUE ONLY FOR SINGLE ASSIGNMENT
// ======================
st.execute("""
    CREATE UNIQUE INDEX IF NOT EXISTS idx_single_active_assignment
    ON ticket_assignments(ticket_id)
    WHERE active = 1 AND assignment_type = 'SINGLE'
""");

// ======================
// INDEX FOR PERFORMANCE
// ======================
st.execute("""
    CREATE INDEX IF NOT EXISTS idx_ticket_assignment_lookup
    ON ticket_assignments(ticket_id, active)
""");

ResultSet rsAssignedTo =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "assigned_to");
if (!rsAssignedTo.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN assigned_to INTEGER");
}

ResultSet rsFromRole =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "from_role");
if (!rsFromRole.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN from_role TEXT");
}

ResultSet rsToRole =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "to_role");
if (!rsToRole.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN to_role TEXT");
}

ResultSet rsType =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "assignment_type");
if (!rsType.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN assignment_type TEXT DEFAULT 'REASSIGNMENT'");
}











st.execute("""
    CREATE TABLE IF NOT EXISTS task_comments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        task_id INTEGER NOT NULL,
        user_id INTEGER NOT NULL,
        comment TEXT NOT NULL,
        created_at DATETIME DEFAULT (DATETIME('now','localtime')),

        FOREIGN KEY(task_id) REFERENCES ticket_tasks(id),
        FOREIGN KEY(user_id) REFERENCES users(id)
    )
""");


st.execute("""
    CREATE TABLE IF NOT EXISTS task_attachments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        task_id INTEGER NOT NULL,
        file_name TEXT,
        file_path TEXT,
        file_type TEXT,
        uploaded_by INTEGER,
        uploaded_at DATETIME DEFAULT (DATETIME('now','localtime')),

        FOREIGN KEY(task_id) REFERENCES ticket_tasks(id),
        FOREIGN KEY(uploaded_by) REFERENCES users(id)
    )
""");


st.execute("""
    CREATE TABLE IF NOT EXISTS task_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        task_id INTEGER NOT NULL,
        user_id INTEGER,
        event_type TEXT,
        description TEXT,
        created_at DATETIME DEFAULT (DATETIME('now','localtime')),

        FOREIGN KEY(task_id) REFERENCES ticket_tasks(id),
        FOREIGN KEY(user_id) REFERENCES users(id)
    )
""");

st.execute("""
    CREATE INDEX IF NOT EXISTS idx_task_assigned
    ON ticket_tasks(assigned_to);
""");

st.execute("""
    CREATE INDEX IF NOT EXISTS idx_task_ticket
    ON ticket_tasks(ticket_id);
""");

st.execute("""
    CREATE INDEX IF NOT EXISTS idx_task_status
    ON ticket_tasks(status);
""");


try {
    st.execute("ALTER TABLE ticket_tasks ADD COLUMN priority TEXT");
} catch (Exception ignored) {}



st.execute("""
           CREATE TABLE IF NOT EXISTS ticket_escalations (
               id INTEGER PRIMARY KEY AUTOINCREMENT,
               ticket_id INTEGER NOT NULL,
               escalated_by INTEGER NOT NULL,
               escalated_from INTEGER,
               escalated_to INTEGER NOT NULL,
               reason TEXT,
               escalated_at DATETIME DEFAULT (DATETIME('now','localtime')),
           
               FOREIGN KEY(ticket_id) REFERENCES tickets(id),
               FOREIGN KEY(escalated_by) REFERENCES users(id),
               FOREIGN KEY(escalated_from) REFERENCES users(id),
               FOREIGN KEY(escalated_to) REFERENCES users(id)
           )
           
           
           """);

            st.execute("""
   CREATE TABLE IF NOT EXISTS ticket_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        ticket_id INTEGER NOT NULL,
        user_id INTEGER,
        event_type TEXT,
        comment TEXT,
        created_at DATETIME DEFAULT (DATETIME('now','localtime')),
    
        FOREIGN KEY(ticket_id) REFERENCES tickets(id),
        FOREIGN KEY(user_id) REFERENCES users(id)
    )
""");
     ResultSet rsDesc =
    c.getMetaData().getColumns(null, null, "ticket_events", "description");

if (!rsDesc.next()) {
    st.execute("ALTER TABLE ticket_events ADD COLUMN description TEXT");
}       
            
            
            
            
            
            st.execute("""
    CREATE TABLE IF NOT EXISTS messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        sender_id INTEGER,
        receiver_id INTEGER,
        message TEXT,
        is_read INTEGER DEFAULT 0,
        created_at DATETIME DEFAULT (DATETIME('now','localtime')),
        FOREIGN KEY(sender_id) REFERENCES users(id),
        FOREIGN KEY(receiver_id) REFERENCES users(id)
    )
""");
            
           
            
            st.execute("""
CREATE TABLE IF NOT EXISTS ticket_comments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    ticket_id INTEGER NOT NULL,
    user_id INTEGER NOT NULL,
    comment TEXT NOT NULL,
    image_path TEXT,
    created_at DATETIME DEFAULT (DATETIME('now','localtime')),
    FOREIGN KEY(ticket_id) REFERENCES tickets(id),
    FOREIGN KEY(user_id) REFERENCES users(id)
)
""");
            st.execute("""
    UPDATE ticket_assignments
    SET assigned_to = agent_id
    WHERE assigned_to IS NULL
""");
            
            st.execute("""
    CREATE INDEX IF NOT EXISTS idx_ticket_assignment
    ON ticket_assignments(ticket_id, assigned_to);
""");
            
            
            st.execute("""
    UPDATE ticket_assignments
    SET assigned_to = agent_id
    WHERE assigned_to IS NULL
""");
            
            
            
            
            st.execute("""
                       CREATE INDEX IF NOT EXISTS idx_ticket_status
                       ON tickets(status);
                        """);
            
            st.execute("""
                       CREATE INDEX IF NOT EXISTS idx_ticket_department
                       ON tickets(department_id);
                       """);
            st.execute(""" 
                       CREATE INDEX IF NOT EXISTS idx_ticket_created
                       ON tickets(created_at);
                       
                       """);
                       st.execute("""
                                  CREATE INDEX IF NOT EXISTS idx_messages_users
                                  ON messages(sender_id, receiver_id);
                                  
                                  """);
                       
                       st.execute("""
CREATE INDEX IF NOT EXISTS idx_ticket_assigned
ON tickets(assigned_to);
""");
           
                       
                       st.execute("""
                                  CREATE TABLE IF NOT EXISTS notifications (
                                  
                                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                                  
                                      user_id INTEGER NOT NULL,
                                  
                                      title TEXT NOT NULL,
                                  
                                      message TEXT NOT NULL,
                                  
                                      type TEXT,
                                      target_type TEXT,
                                      target_id INTEGER,
                                      target_ref TEXT,
                                  
                                      is_read INTEGER DEFAULT 0,
                                  
                                      created_at DATETIME DEFAULT (DATETIME('now','localtime'))
                                  
                                  )
                       
                                  """);

                       try { st.execute("ALTER TABLE notifications ADD COLUMN target_type TEXT"); } catch(Exception ignored){}
                       try { st.execute("ALTER TABLE notifications ADD COLUMN target_id INTEGER"); } catch(Exception ignored){}
                       try { st.execute("ALTER TABLE notifications ADD COLUMN target_ref TEXT"); } catch(Exception ignored){}
                       
                       
                                           
                       
                       
        st.execute("""
    CREATE TABLE IF NOT EXISTS datashare_files (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        file_name TEXT,
        file_path TEXT,
        shared_by_id INTEGER,
        shared_by_name TEXT,
        shared_role TEXT,
        sous_direction TEXT,
        date_shared TEXT,
        time_shared TEXT,
        expiration_date TEXT,
        file_size INTEGER DEFAULT 0,
        downloads INTEGER DEFAULT 0
    )
""");

// SAFE MIGRATIONS (for older databases)

try {
    st.execute("ALTER TABLE datashare_files ADD COLUMN file_size INTEGER DEFAULT 0");
} catch (Exception ignored) {}

try {
    st.execute("ALTER TABLE datashare_files ADD COLUMN downloads INTEGER DEFAULT 0");
} catch (Exception ignored) {}
        
        
        st.execute("""
                   CREATE TABLE IF NOT EXISTS datashare_recipients (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       file_id INTEGER,
                       recipient_id INTEGER,
                       recipient_name TEXT,
                       recipient_role TEXT,
                       otp_hash TEXT,
                       otp_expires_at TEXT,
                       otp_verified_at TEXT
                   )
                   """);

try {
    st.execute("ALTER TABLE datashare_recipients ADD COLUMN otp_hash TEXT");
} catch (Exception ignored) {}
try {
    st.execute("ALTER TABLE datashare_recipients ADD COLUMN otp_expires_at TEXT");
} catch (Exception ignored) {}
try {
    st.execute("ALTER TABLE datashare_recipients ADD COLUMN otp_verified_at TEXT");
} catch (Exception ignored) {}
            
        
        st.execute("""
                  
                   CREATE TABLE IF NOT EXISTS datashare_audit (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       file_id INTEGER,
                       file_name TEXT,
                       shared_by_id INTEGER,
                       shared_by_name TEXT,
                       recipient_id INTEGER,
                       recipient_name TEXT,
                       action TEXT,
                       date TEXT,
                       time TEXT
                   )

                   """);
        
        
        
        st.execute("""
                    CREATE TABLE IF NOT EXISTS datashare_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        file_id INTEGER,
                        sender_id INTEGER,
                        sender_name TEXT,
                        message TEXT,
                        date_sent TEXT,
                        time_sent TEXT
                    )
                   """);
        
        
        st.execute("""
                   CREATE TABLE IF NOT EXISTS ticket_history (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       ticket_id INTEGER,
                       action TEXT,
                       description TEXT,
                       user_id INTEGER,
                       username TEXT,
                       created_at DATETIME DEFAULT (DATETIME('now','localtime'))
                   )
                  
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS ticket_close_requests (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       ticket_id INTEGER NOT NULL,
                       requested_by INTEGER NOT NULL,
                       requested_to INTEGER NOT NULL,
                       reason TEXT,
                       status TEXT DEFAULT 'PENDING',
                       requested_at DATETIME DEFAULT (DATETIME('now','localtime')),
                       decided_by INTEGER,
                       decided_at DATETIME,
                       decision_note TEXT,
                       FOREIGN KEY(ticket_id) REFERENCES tickets(id),
                       FOREIGN KEY(requested_by) REFERENCES users(id),
                       FOREIGN KEY(requested_to) REFERENCES users(id),
                       FOREIGN KEY(decided_by) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS ticket_external_escalations (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       ticket_id INTEGER NOT NULL,
                       from_direction_id INTEGER,
                       to_direction_id INTEGER NOT NULL,
                       to_sous_direction_id INTEGER NOT NULL,
                       escalated_by INTEGER NOT NULL,
                       status TEXT DEFAULT 'PENDING',
                       note TEXT,
                       requested_approver_id INTEGER,
                       approved_by INTEGER,
                       approved_at DATETIME,
                       assigned_to INTEGER,
                       assigned_at DATETIME,
                       created_at DATETIME DEFAULT (DATETIME('now','localtime')),
                       FOREIGN KEY(ticket_id) REFERENCES tickets(id),
                       FOREIGN KEY(from_direction_id) REFERENCES directions(id),
                       FOREIGN KEY(to_direction_id) REFERENCES directions(id),
                       FOREIGN KEY(to_sous_direction_id) REFERENCES sous_directions(id),
                       FOREIGN KEY(escalated_by) REFERENCES users(id),
                       FOREIGN KEY(requested_approver_id) REFERENCES users(id),
                       FOREIGN KEY(approved_by) REFERENCES users(id),
                       FOREIGN KEY(assigned_to) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS automated_jobs (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       job_title TEXT NOT NULL,
                       job_description TEXT,
                       due_at TEXT NOT NULL,
                       reminder_minutes INTEGER DEFAULT 60,
                       assignee_user_id INTEGER,
                       created_by INTEGER,
                       recurrence TEXT DEFAULT 'ONCE',
                       active INTEGER DEFAULT 1,
                       last_reminder_at TEXT,
                       last_ticket_created_at TEXT,
                       created_at DATETIME DEFAULT (DATETIME('now','localtime')),
                       FOREIGN KEY(assignee_user_id) REFERENCES users(id),
                       FOREIGN KEY(created_by) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS monthly_reports (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       month_key TEXT NOT NULL UNIQUE,
                       generated_at DATETIME DEFAULT (DATETIME('now','localtime')),
                       file_path TEXT,
                       total_tickets INTEGER DEFAULT 0,
                       open_tickets INTEGER DEFAULT 0,
                       in_progress_tickets INTEGER DEFAULT 0,
                       closed_tickets INTEGER DEFAULT 0
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS field_missions (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       mission_code TEXT NOT NULL UNIQUE,
                       title TEXT NOT NULL,
                       site_location TEXT,
                       start_date TEXT,
                       end_date TEXT,
                       description TEXT,
                       objectives TEXT,
                       status TEXT NOT NULL DEFAULT 'PLANNED',
                       report_text TEXT,
                       report_submitted_at TEXT,
                       report_author_id INTEGER,
                       lead_user_id INTEGER,
                       created_by INTEGER NOT NULL,
                       order_reference TEXT,
                       order_issue_date TEXT,
                       order_issued_by TEXT,
                       order_body TEXT,
                       created_at TEXT DEFAULT (datetime('now','localtime')),
                       updated_at TEXT DEFAULT (datetime('now','localtime')),
                       FOREIGN KEY(lead_user_id) REFERENCES users(id),
                       FOREIGN KEY(created_by) REFERENCES users(id),
                       FOREIGN KEY(report_author_id) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS field_mission_participants (
                       mission_id INTEGER NOT NULL,
                       user_id INTEGER NOT NULL,
                       PRIMARY KEY (mission_id, user_id),
                       FOREIGN KEY(mission_id) REFERENCES field_missions(id) ON DELETE CASCADE,
                       FOREIGN KEY(user_id) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS field_mission_attachments (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       mission_id INTEGER NOT NULL,
                       file_name TEXT NOT NULL,
                       file_path TEXT NOT NULL,
                       uploaded_by INTEGER,
                       uploaded_at TEXT DEFAULT (datetime('now','localtime')),
                       FOREIGN KEY(mission_id) REFERENCES field_missions(id) ON DELETE CASCADE,
                       FOREIGN KEY(uploaded_by) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS field_mission_order_attachments (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       mission_id INTEGER NOT NULL,
                       file_name TEXT NOT NULL,
                       file_path TEXT NOT NULL,
                       uploaded_by INTEGER,
                       uploaded_at TEXT DEFAULT (datetime('now','localtime')),
                       FOREIGN KEY(mission_id) REFERENCES field_missions(id) ON DELETE CASCADE,
                       FOREIGN KEY(uploaded_by) REFERENCES users(id)
                   )
                   """);

        st.execute("""
                   CREATE TABLE IF NOT EXISTS field_mission_detail_attachments (
                       id INTEGER PRIMARY KEY AUTOINCREMENT,
                       mission_id INTEGER NOT NULL,
                       file_name TEXT NOT NULL,
                       file_path TEXT NOT NULL,
                       uploaded_by INTEGER,
                       uploaded_at TEXT DEFAULT (datetime('now','localtime')),
                       FOREIGN KEY(mission_id) REFERENCES field_missions(id) ON DELETE CASCADE,
                       FOREIGN KEY(uploaded_by) REFERENCES users(id)
                   )
                   """);

        // Physical courier (internal mail) tracking — same feature as sysco-ticket patterns
        st.execute("""
                CREATE TABLE IF NOT EXISTS courier_packets (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ref_code TEXT NOT NULL UNIQUE,
                    title TEXT NOT NULL,
                    description TEXT,
                    status TEXT NOT NULL,
                    target_direction_id INTEGER,
                    target_sous_direction_id INTEGER,
                    created_by INTEGER NOT NULL,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    assigned_sous_directeur_id INTEGER,
                    assigned_inspecteur_id INTEGER,
                    assigned_controleur_id INTEGER,
                    assigned_verificateur_id INTEGER,
                    resolved_at TEXT,
                    resolved_by INTEGER,
                    FOREIGN KEY(created_by) REFERENCES users(id),
                    FOREIGN KEY(target_direction_id) REFERENCES directions(id),
                    FOREIGN KEY(target_sous_direction_id) REFERENCES sous_directions(id),
                    FOREIGN KEY(assigned_sous_directeur_id) REFERENCES users(id),
                    FOREIGN KEY(assigned_inspecteur_id) REFERENCES users(id),
                    FOREIGN KEY(assigned_controleur_id) REFERENCES users(id),
                    FOREIGN KEY(assigned_verificateur_id) REFERENCES users(id),
                    FOREIGN KEY(resolved_by) REFERENCES users(id)
                )
                """);
        st.execute("""
                CREATE TABLE IF NOT EXISTS courier_journey_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    packet_id INTEGER NOT NULL,
                    event_type TEXT NOT NULL,
                    at_time TEXT NOT NULL DEFAULT (datetime('now','localtime')),
                    actor_user_id INTEGER,
                    related_user_id INTEGER,
                    direction_id INTEGER,
                    sous_direction_id INTEGER,
                    note TEXT,
                    FOREIGN KEY(packet_id) REFERENCES courier_packets(id) ON DELETE CASCADE,
                    FOREIGN KEY(actor_user_id) REFERENCES users(id),
                    FOREIGN KEY(related_user_id) REFERENCES users(id),
                    FOREIGN KEY(direction_id) REFERENCES directions(id),
                    FOREIGN KEY(sous_direction_id) REFERENCES sous_directions(id)
                )
                """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_courier_packets_direction ON courier_packets(target_direction_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_courier_packets_sous ON courier_packets(target_sous_direction_id)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_courier_journey_packet ON courier_journey_events(packet_id)");

        ResultSet rCp;
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "sender");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN sender TEXT");
        }
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "priority");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN priority TEXT DEFAULT 'MEDIUM'");
        }
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "registration_date");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN registration_date TEXT");
        }
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "attachment_path");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN attachment_path TEXT");
        }
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "secretaire_can_route_sous");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN secretaire_can_route_sous INTEGER NOT NULL DEFAULT 0");
        }
        rCp = c.getMetaData().getColumns(null, null, "courier_packets", "linked_ticket_id");
        if (!rCp.next()) {
            st.execute("ALTER TABLE courier_packets ADD COLUMN linked_ticket_id INTEGER");
        }

        st.execute("""
                CREATE TABLE IF NOT EXISTS courier_packet_extra_directions (
                    packet_id INTEGER NOT NULL,
                    direction_id INTEGER NOT NULL,
                    PRIMARY KEY (packet_id, direction_id),
                    FOREIGN KEY(packet_id) REFERENCES courier_packets(id) ON DELETE CASCADE,
                    FOREIGN KEY(direction_id) REFERENCES directions(id)
                )
                """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_cped_direction ON courier_packet_extra_directions (direction_id)");

        // MyShift (presence, face + location metadata)
        st.execute("""
                CREATE TABLE IF NOT EXISTS myshift_signins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    signin_day TEXT NOT NULL,
                    sign_in_time TEXT NOT NULL,
                    sign_out_time TEXT,
                    latitude REAL,
                    longitude REAL,
                    location_text TEXT,
                    country_code TEXT,
                    city TEXT,
                    ip_address TEXT,
                    face_verified INTEGER NOT NULL DEFAULT 0,
                    face_confidence REAL,
                    face_method TEXT,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    FOREIGN KEY(user_id) REFERENCES users(id)
                )
                """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_myshift_user_day ON myshift_signins(user_id, signin_day)");
        st.execute("CREATE INDEX IF NOT EXISTS idx_myshift_signin_day ON myshift_signins(signin_day)");
        st.execute("""
                CREATE TABLE IF NOT EXISTS myshift_planned (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    work_date TEXT NOT NULL,
                    shift_code TEXT NOT NULL,
                    created_by INTEGER,
                    created_at TEXT DEFAULT (datetime('now','localtime')),
                    UNIQUE (user_id, work_date),
                    FOREIGN KEY (user_id) REFERENCES users (id)
                )
                """);
        st.execute("CREATE INDEX IF NOT EXISTS idx_msplan_user_date ON myshift_planned(user_id, work_date)");

        ResultSet rsFmDesc =
                c.getMetaData().getColumns(null, null, "field_missions", "description");
        if (!rsFmDesc.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN description TEXT");
        }

        ResultSet rsOrdRef =
                c.getMetaData().getColumns(null, null, "field_missions", "order_reference");
        if (!rsOrdRef.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN order_reference TEXT");
        }
        ResultSet rsOrdDate =
                c.getMetaData().getColumns(null, null, "field_missions", "order_issue_date");
        if (!rsOrdDate.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN order_issue_date TEXT");
        }
        ResultSet rsOrdBy =
                c.getMetaData().getColumns(null, null, "field_missions", "order_issued_by");
        if (!rsOrdBy.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN order_issued_by TEXT");
        }
        ResultSet rsOrdBody =
                c.getMetaData().getColumns(null, null, "field_missions", "order_body");
        if (!rsOrdBody.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN order_body TEXT");
        }
        ResultSet rsRepAuth =
                c.getMetaData().getColumns(null, null, "field_missions", "report_author_id");
        if (!rsRepAuth.next()) {
            st.execute("ALTER TABLE field_missions ADD COLUMN report_author_id INTEGER REFERENCES users(id)");
            st.execute("""
                    UPDATE field_missions SET report_author_id = created_by
                    WHERE report_author_id IS NULL AND report_text IS NOT NULL AND TRIM(report_text) <> ''
                    """);
        }

            // =====================================================
            // SAFE MIGRATIONS - ticket_assignments upgrades
            // =====================================================

ResultSet rsStatus =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "status");
if (!rsStatus.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN status TEXT DEFAULT 'ASSIGNED'");
}

ResultSet rsStarted =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "started_at");
if (!rsStarted.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN started_at DATETIME");
}

ResultSet rsClosed =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "closed_at");
if (!rsClosed.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN closed_at DATETIME");
}

ResultSet rsDuration =
    c.getMetaData().getColumns(null, null, "ticket_assignments", "duration_minutes");
if (!rsDuration.next()) {
    st.execute("ALTER TABLE ticket_assignments ADD COLUMN duration_minutes INTEGER");
}
            

ResultSet rsBreach =
    c.getMetaData().getColumns(null, null, "tickets", "sla_breached");

if (!rsBreach.next()) {
    st.execute("ALTER TABLE tickets ADD COLUMN sla_breached INTEGER DEFAULT 0");
}
            
            
            
            
            
            // ===============================
// USERS EMAIL COLUMN
// ===============================
ResultSet rsEmail =
    c.getMetaData().getColumns(null, null, "users", "email");

if (!rsEmail.next()) {
    st.execute("ALTER TABLE users ADD COLUMN email TEXT");
}
            
            
            // =====================================================
            // SAFE MIGRATIONS
            // =====================================================
            ResultSet rs;

            rs = c.getMetaData().getColumns(null, null, "users", "direction_id");
            if (!rs.next()) {
                st.execute("ALTER TABLE users ADD COLUMN direction_id INTEGER");
            }

            rs = c.getMetaData().getColumns(null, null, "tickets", "ticket_type");
            if (!rs.next()) {
                st.execute("ALTER TABLE tickets ADD COLUMN ticket_type TEXT NOT NULL DEFAULT 'INTERNAL'");
            }

            ResultSet rsAssigned =
            c.getMetaData().getColumns(null, null, "tickets", "assigned_to");

            if (!rsAssigned.next()) {
            st.execute("ALTER TABLE tickets ADD COLUMN assigned_to INTEGER");
                }
            
            
            
            // ===============================
// DEPARTMENTS ESCALATION COLUMNS
// ===============================

ResultSet rsEsc1 =
    c.getMetaData().getColumns(null, null, "departments", "escalation_level1");

if (!rsEsc1.next()) {
    st.execute("ALTER TABLE departments ADD COLUMN escalation_level1 INTEGER");
}

ResultSet rsEsc2 =
    c.getMetaData().getColumns(null, null, "departments", "escalation_level2");

if (!rsEsc2.next()) {
    st.execute("ALTER TABLE departments ADD COLUMN escalation_level2 INTEGER");
}

ResultSet rsExtReqApprover =
    c.getMetaData().getColumns(null, null, "ticket_external_escalations", "requested_approver_id");
if (!rsExtReqApprover.next()) {
    st.execute("ALTER TABLE ticket_external_escalations ADD COLUMN requested_approver_id INTEGER");
}

ResultSet rsExtApprovedBy =
    c.getMetaData().getColumns(null, null, "ticket_external_escalations", "approved_by");
if (!rsExtApprovedBy.next()) {
    st.execute("ALTER TABLE ticket_external_escalations ADD COLUMN approved_by INTEGER");
}

ResultSet rsExtApprovedAt =
    c.getMetaData().getColumns(null, null, "ticket_external_escalations", "approved_at");
if (!rsExtApprovedAt.next()) {
    st.execute("ALTER TABLE ticket_external_escalations ADD COLUMN approved_at DATETIME");
}

ResultSet rsJobReminder =
    c.getMetaData().getColumns(null, null, "automated_jobs", "last_reminder_at");
if (!rsJobReminder.next()) {
    st.execute("ALTER TABLE automated_jobs ADD COLUMN last_reminder_at TEXT");
}

ResultSet rsJobLastTicket =
    c.getMetaData().getColumns(null, null, "automated_jobs", "last_ticket_created_at");
if (!rsJobLastTicket.next()) {
    st.execute("ALTER TABLE automated_jobs ADD COLUMN last_ticket_created_at TEXT");
}
            if (!columnExists(c, "tickets", "ticket_number")) {

    try (Statement stmt = c.createStatement()) {

        stmt.executeUpdate(
            "ALTER TABLE tickets ADD COLUMN ticket_number TEXT"
        );
    }
}
            
            
            // =====================================================
            // DEFAULT ADMIN
            // =====================================================
            rs = st.executeQuery("SELECT COUNT(*) FROM users");

            if (rs.next() && rs.getInt(1) == 0) {

                String hash = BCrypt.hashpw("admin123", BCrypt.gensalt());

                PreparedStatement psAdmin = c.prepareStatement(
                        "INSERT INTO users(username, password_hash, role, active) VALUES (?, ?, ?, ?)"
                );

                psAdmin.setString(1, "admin");
                psAdmin.setString(2, hash);
                psAdmin.setString(3, "ADMIN");
                psAdmin.setInt(4, 1);

                psAdmin.executeUpdate();

                System.out.println("Default admin created (admin / admin123)");
            }

            com.app.dao.DirectionDAO.applyStripParentheticalsAndMergeDuplicates(c);
            purgeLegacyAcronymSousDirections(c);

            System.out.println("Database initialized successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
       
    }
    
    private static int getOrCreateSousDirectionId(Connection c, String name) throws SQLException {
        try (PreparedStatement sel = c.prepareStatement("SELECT id FROM sous_directions WHERE name = ?")) {
            sel.setString(1, name);
            ResultSet rs = sel.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        try (PreparedStatement ins = c.prepareStatement("INSERT INTO sous_directions(name) VALUES (?)")) {
            ins.setString(1, name);
            ins.executeUpdate();
        }
        try (PreparedStatement sel = c.prepareStatement("SELECT id FROM sous_directions WHERE name = ?")) {
            sel.setString(1, name);
            ResultSet rs = sel.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        throw new SQLException("Failed to create sous direction: " + name);
    }

    private static int getOrCreateDirectionId(Connection c, String name, int defaultSousDirectionId) throws SQLException {
        try (PreparedStatement sel = c.prepareStatement("SELECT id FROM directions WHERE name = ?")) {
            sel.setString(1, name);
            ResultSet rs = sel.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT INTO directions(name, sous_direction_id) VALUES (?, ?)")) {
            ins.setString(1, name);
            ins.setInt(2, defaultSousDirectionId);
            ins.executeUpdate();
        }
        try (PreparedStatement sel = c.prepareStatement("SELECT id FROM directions WHERE name = ?")) {
            sel.setString(1, name);
            ResultSet rs = sel.executeQuery();
            if (rs.next()) return rs.getInt("id");
        }
        throw new SQLException("Failed to create direction: " + name);
    }

    private static void linkDirectionSousDirection(Connection c, int directionId, int sousDirectionId) throws SQLException {
        if (DbConfig.isOracle()) {
            try (PreparedStatement ps = c.prepareStatement("""
                    MERGE INTO direction_sous_direction_map t
                    USING (SELECT ? AS direction_id, ? AS sous_direction_id FROM dual) s
                    ON (t.direction_id = s.direction_id AND t.sous_direction_id = s.sous_direction_id)
                    WHEN NOT MATCHED THEN INSERT (direction_id, sous_direction_id)
                    VALUES (s.direction_id, s.sous_direction_id)
                    """)) {
                ps.setInt(1, directionId);
                ps.setInt(2, sousDirectionId);
                ps.executeUpdate();
            }
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT OR IGNORE INTO direction_sous_direction_map(direction_id, sous_direction_id) VALUES (?, ?)")) {
            ps.setInt(1, directionId);
            ps.setInt(2, sousDirectionId);
            ps.executeUpdate();
        }
    }

    /**
     * Drops legacy acronym-only sous directions (SDMA, SDRM, SDSYD). Reference data now uses
     * full names (see {@link #seedDirectionSousDirectionMappings}). Re-runnable on each startup.
     */
    public static void purgeLegacyAcronymSousDirections(Connection c) {
        final String[] legacy = {"SDMA", "SDRM", "SDSYD"};
        final String replacementName = "Développement et Maintenance des Applications";
        Integer replacementId = null;
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM sous_directions WHERE name = ?")) {
            ps.setString(1, replacementName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    replacementId = rs.getInt(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        for (String name : legacy) {
            int sid;
            try (PreparedStatement q = c.prepareStatement("SELECT id FROM sous_directions WHERE name = ?")) {
                q.setString(1, name);
                try (ResultSet rs = q.executeQuery()) {
                    if (!rs.next()) {
                        continue;
                    }
                    sid = rs.getInt(1);
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            try (PreparedStatement u = c.prepareStatement("UPDATE users SET sous_direction_id = NULL WHERE sous_direction_id = ?")) {
                u.setInt(1, sid);
                u.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE courier_packets SET target_sous_direction_id = NULL WHERE target_sous_direction_id = ?")) {
                u.setInt(1, sid);
                u.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try (PreparedStatement u = c.prepareStatement(
                    "UPDATE courier_journey_events SET sous_direction_id = NULL WHERE sous_direction_id = ?")) {
                u.setInt(1, sid);
                u.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (replacementId != null) {
                try (PreparedStatement u = c.prepareStatement(
                        "UPDATE ticket_external_escalations SET to_sous_direction_id = ? WHERE to_sous_direction_id = ?")) {
                    u.setInt(1, replacementId);
                    u.setInt(2, sid);
                    u.executeUpdate();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try (PreparedStatement d = c.prepareStatement("DELETE FROM direction_sous_direction_map WHERE sous_direction_id = ?")) {
                d.setInt(1, sid);
                d.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try (PreparedStatement d = c.prepareStatement("DELETE FROM sous_directions WHERE id = ?")) {
                d.setInt(1, sid);
                d.executeUpdate();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static void seedDirectionSousDirectionMappings(Connection c, int autreId) throws SQLException {
        String[][] mapping = new String[][]{
                {"Direction de la Réglementation et de la Facilitation", "Réglementation|Facilitation"},
                {"Direction de la Lutte contre la Fraude", "Liaison et Renseignements|Stratégies et Planification|Audit a posteriori"},
                {"Direction du Tarif et des Règles d’Origine", "Tarif|Règles d'origine"},
                {"Direction de la Valeur", "Évaluation|Recours et valeurs de base"},
                {"Direction des Autres Produits d’Accises", "Alcools, Boissons alcooliques et Limonades|Tabacs et autres Produits d’Accises"},
                {"Direction des Huiles Minérales", "Producteurs|Distributeurs"},
                {"Direction des Recettes du Trésor", "Recettes de Douanes|Recettes des Accises|Budget et Recettes Connexes"},
                {"Direction des Ressources Humaines", "Recrutement et Formation|Administration|OEuvres Sociales|Relations Publiques et Protocole"},
                {"Direction des Équipements et de la Logistique", "Gestion du Patrimoine|Imprimerie et Approvisionnements"},
                {"Direction des Équipements et Logistique", "Gestion du Patrimoine|Imprimerie et Approvisionnements"},
                {"Direction des Statistiques, Documentation et Études Économiques", "Statistiques et Études Économiques|Documentation"},
                {"Direction des Affaires Juridiques et Contentieuses", "Affaires Contentieuses|Affaires Juridiques"},
                {DirectionDAO.CANONICAL_DIRECTION_SYSTEMES_TI, "Développement et Maintenance des Applications|Réseaux, Télécommunications et Maintenance Hardware|Sydonia"},
                {"Direction de l’Audit Interne", "AUTRE"},
                {"Direction des Finances Internes", "Comptabilité et Trésorerie|Budget Interne"},
                {"Direction des Réformes et Modernisation", "AUTRE"},
                {"Direction des Réformes et de la Modernisation", "AUTRE"},
                {"Bureau de Coordination", "AUTRE"}
        };

        for (String[] row : mapping) {
            String directionName = row[0];
            String[] sousNames = row[1].split("\\|");
            int directionId = getOrCreateDirectionId(c, directionName, autreId);
            for (String sousNameRaw : sousNames) {
                String sousName = sousNameRaw == null ? "" : sousNameRaw.trim();
                if (sousName.isEmpty()) continue;
                int sousId = "AUTRE".equalsIgnoreCase(sousName)
                        ? autreId
                        : getOrCreateSousDirectionId(c, sousName);
                linkDirectionSousDirection(c, directionId, sousId);
            }
        }
    }
    
    
    public static boolean columnExists(Connection conn, String table, String column) {
        try {
            DatabaseMetaData md = conn.getMetaData();
            if (DbConfig.isOracle()) {
                String schema = conn.getSchema();
                if (schema == null || schema.isEmpty()) {
                    schema = md.getUserName();
                }
                try (ResultSet rs = md.getColumns(null, schema, table.toUpperCase(), column.toUpperCase())) {
                    return rs.next();
                }
            }
            try (ResultSet rs = md.getColumns(null, null, table, column)) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }
}