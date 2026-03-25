package com.app.util;

import com.app.model.Ticket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Year;

public class TicketUtil {

  public static String formatTicketRef(int ticketId) {

    String base = "TCK-" + Year.now().getValue() + "-" + String.format("%03d", ticketId);

    try (Connection c = DB.getConnection()) {

        String sql = """
            SELECT id
            FROM tickets
            WHERE merged_into = ?
        """;

        PreparedStatement ps = c.prepareStatement(sql);
        ps.setInt(1, ticketId);

        ResultSet rs = ps.executeQuery();

        StringBuilder merged = new StringBuilder();

        while (rs.next()) {
            merged.append("/").append(String.format("%02d", rs.getInt("id")));
        }

        if (merged.length() > 0) {
            return "TCKM-" + Year.now().getValue()
                    + "-" + String.format("%03d", ticketId)
                    + merged;
        }

    } catch (Exception e) {
        e.printStackTrace();
    }

    return base;
}
}