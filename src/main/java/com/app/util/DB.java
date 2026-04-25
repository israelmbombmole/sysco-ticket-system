package com.app.util;

import org.mindrot.jbcrypt.BCrypt;

import java.sql.*;

public class DB {

    private static final String URL =
            "jdbc:sqlite:C:/sqlite/javafx-audit-system/app.db";

    public static Connection getConnection() throws SQLException {

    Connection conn = DriverManager.getConnection(URL);

    try (Statement st = conn.createStatement()) {

        st.execute("PRAGMA foreign_keys = ON;");
        st.execute("PRAGMA busy_timeout = 5000;"); // wait 5 seconds if locked
        st.execute("PRAGMA timezone = 'localtime';");

    }

    return conn;
}

    public static void init() {

        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            System.out.println("USING DB FILE: " + URL);

            // =====================================================
            // SOUS DIRECTIONS
            // =====================================================
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sous_directions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name TEXT NOT NULL UNIQUE
                    )
                    """);

            st.executeUpdate("INSERT OR IGNORE INTO sous_directions(name) VALUES ('SDMA')");
            st.executeUpdate("INSERT OR IGNORE INTO sous_directions(name) VALUES ('SDSYD')");
            st.executeUpdate("INSERT OR IGNORE INTO sous_directions(name) VALUES ('SDRM')");
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
    "Direction des Systèmes et Technologies de l’Information (DSTI)",
    "Direction de l’Audit Interne",
    "Direction des Réformes et de la Modernisation"
};

                for (String name : directionNames) {
                    psDir.setString(1, name);
                    psDir.setInt(2, autreId);
                    psDir.executeUpdate();
                }
            }

            // =====================================================
            // USERS
            // =====================================================
            st.execute("""
                    CREATE TABLE IF NOT EXISTS users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT UNIQUE NOT NULL,
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
try { st.execute("ALTER TABLE users ADD COLUMN is_super_admin INTEGER DEFAULT 0"); } catch(Exception ignored){}
try {
    st.executeUpdate("""
        UPDATE users
        SET is_super_admin = 1
        WHERE username = 'admin'
    """);
} catch (Exception ignored) {}

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
                INSERT INTO users(username, password_hash, role, active, failed_attempts, must_change_password, is_super_admin)
                VALUES (?, ?, 'ADMIN', 1, 0, 0, ?)
            """);

            ps.setString(1, username);
            ps.setString(2, BCrypt.hashpw(password, BCrypt.gensalt()));
            ps.setInt(3, "admin".equalsIgnoreCase(username) ? 1 : 0);

            ps.executeUpdate();

            System.out.println("✅ Created default admin: " + username);

        } else {

            // 🔥 FORCE RESET (VERY IMPORTANT)
            PreparedStatement update = c.prepareStatement("""
                UPDATE users
                SET password_hash = ?,
                    active = 1,
                    failed_attempts = 0,
                    is_super_admin = CASE WHEN username = 'admin' THEN 1 ELSE is_super_admin END
                WHERE username = ?
            """);

            update.setString(1, BCrypt.hashpw(password, BCrypt.gensalt()));
            update.setString(2, username);

            update.executeUpdate();

            if ("admin".equalsIgnoreCase(username)) {
                try (PreparedStatement markSuperAdmin = c.prepareStatement(
                        "UPDATE users SET is_super_admin = 1 WHERE username = 'admin'")) {
                    markSuperAdmin.executeUpdate();
                }
            }

            System.out.println("🔄 Reset admin: " + username);
        }
    }

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
    ('Direction des Systèmes et Technologies d’information'),
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

ResultSet rsUserPermUnique =
    c.getMetaData().getIndexInfo(null, null, "user_permissions", true, false);
boolean hasUserPermUnique = false;
while (rsUserPermUnique.next()) {
    String idx = rsUserPermUnique.getString("INDEX_NAME");
    if ("idx_user_permissions_unique".equalsIgnoreCase(idx)) {
        hasUserPermUnique = true;
        break;
    }
}
if (!hasUserPermUnique) {
    st.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS idx_user_permissions_unique
        ON user_permissions(user_id, permission)
    """);
}

try (PreparedStatement insertCourierPerms = c.prepareStatement(
        "INSERT OR IGNORE INTO user_permissions(user_id, permission) " +
        "SELECT id, ? FROM users WHERE role='COURRIER'")) {
    String[] courierPerms = {"DASHBOARD", "TICKET_MONITORING", "CREATE_TICKET"};
    for (String perm : courierPerms) {
        insertCourierPerms.setString(1, perm);
        insertCourierPerms.executeUpdate();
    }
}

try (PreparedStatement insertSecretairePerms = c.prepareStatement(
        "INSERT OR IGNORE INTO user_permissions(user_id, permission) " +
        "SELECT id, ? FROM users WHERE role='SECRETAIRE'")) {
    String[] secretairePerms = {"DASHBOARD", "TICKET_MONITORING", "CREATE_TICKET"};
    for (String perm : secretairePerms) {
        insertSecretairePerms.setString(1, perm);
        insertSecretairePerms.executeUpdate();
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
try { st.execute("ALTER TABLE tickets ADD COLUMN date_enregistrement TEXT"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN expediteur TEXT"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN objet TEXT"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN cotation TEXT"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN date_cotation TEXT"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN sous_direction_id INTEGER"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN routing_stage TEXT DEFAULT 'CREATED'"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN recorded_year INTEGER"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN recorded_at DATETIME"); } catch (Exception ignored) {}
try { st.execute("ALTER TABLE tickets ADD COLUMN cotation_assigned_at DATETIME"); } catch (Exception ignored) {}
try {
    st.executeUpdate("""
        UPDATE tickets
        SET routing_stage = COALESCE(routing_stage, 'CREATED')
    """);
} catch (Exception ignored) {}


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
                                  
                                      is_read INTEGER DEFAULT 0,
                                  
                                      created_at DATETIME DEFAULT (DATETIME('now','localtime'))
                                  
                                  )
                       
                                  """);
                       
                       
                                           
                       
                       
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
                       recipient_role TEXT
                   )
                   """);
            
        
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

            System.out.println("Database initialized successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
       
    }
    
    
    public static boolean columnExists(Connection conn, String table, String column) {

    try (ResultSet rs = conn.getMetaData()
            .getColumns(null, null, table, column)) {

        return rs.next();

    } catch (Exception e) {
        return false;
    }
}
}