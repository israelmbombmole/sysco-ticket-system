package com.app.service;

import com.app.dao.UserDAO;
import com.app.model.Ticket;

import java.util.List;
import java.util.Properties;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Message;
import jakarta.mail.Transport;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

public class EmailService {

    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";

    // Sender email
    private static final String EMAIL = "sysco.ticketing@gmail.com";

    // Gmail App Password
    private static final String PASSWORD = "ahimdbdnzerarltn";

    public static void notifyAdmins(Ticket ticket) {

        try {

            List<String> admins = UserDAO.getAdminEmails();

            if (admins == null || admins.isEmpty()) {
                System.out.println("No admin emails found.");
                return;
            }

            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", SMTP_HOST);
            props.put("mail.smtp.port", SMTP_PORT);

            Session session = Session.getInstance(
                    props,
                    new Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(EMAIL, PASSWORD);
                        }
                    }
            );

            // Priority color
            String priorityColor =
                    ticket.getPriority().equalsIgnoreCase("HIGH") ? "#dc2626" :
                    ticket.getPriority().equalsIgnoreCase("MEDIUM") ? "#f59e0b" :
                    "#16a34a";

            for (String adminEmail : admins) {

                Message message = new MimeMessage(session);

                message.setFrom(new InternetAddress(EMAIL));

                message.setRecipients(
                        Message.RecipientType.TO,
                        InternetAddress.parse(adminEmail)
                );

                message.setSubject("SYSCO Nouveau Ticket créé | " + ticket.getTicketNumber());

                String body =
                        "<html>" +
                        "<body style='font-family:Arial, sans-serif; background:#f4f6f9; padding:20px;'>" +

                        "<div style='max-width:600px; margin:auto; background:white; border-radius:8px; padding:25px; box-shadow:0 2px 6px rgba(0,0,0,0.1);'>" +

                        "<h2 style='color:#2563eb; margin-bottom:5px;'>📌 Notification de ticket SYSCO</h2>" +
                        "<p style='color:#555;'>Un nouveau ticket a été créé dans le système SYSCO</p>" +

                        "<table style='width:100%; border-collapse:collapse; margin-top:15px;'>" +

                        "<tr>" +
                        "<td style='padding:8px; font-weight:bold;'>Identifiant du Ticket:</td>" +
                        "<td style='padding:8px;'>" + ticket.getTicketNumber() + "</td>" +
                        "</tr>" +

                        "<tr>" +
                        "<td style='padding:8px; font-weight:bold;'>Titre:</td>" +
                        "<td style='padding:8px;'>" + ticket.getTitle() + "</td>" +
                        "</tr>" +
                        
                        "<tr>" +
                        "<td style='padding:8px; font-weight:bold;'>Description:</td>" +
                        "<td style='padding:8px;'>" + ticket.getDescription() + "</td>" +
                        "</tr>" +
                        
                        
                        

                        "<tr>" +
                        "<td style='padding:8px; font-weight:bold;'>Priorité:</td>" +
                        "<td style='padding:8px;'>" +
                        "<span style='background:" + priorityColor + "; color:white; padding:4px 10px; border-radius:6px; font-weight:bold;'>" +
                        ticket.getPriority() +
                        "</span>" +
                        "</td>" +
                        "</tr>" +

                        "</table>" +

                        "<div style='margin-top:25px; text-align:center;'>" +
                        "<a href='#' style='background:#2563eb; color:white; padding:12px 20px; text-decoration:none; border-radius:6px; font-weight:bold;'>Pour consulter ce ticket, veuillez vous connecter à SYSCO.</a>" +
                        "</div>" +
                        
                        "<div style='margin-top:25px; text-align:center;'>" +     
                        "<p style='margin-top:25px; font-size:12px; color:#777;'>SYSCO votre Système de Suivi et de Coordination</p>" +
                        "</div>" +
                        
                        "</div>" +
                        "</body>" +
                        "</html>";

                message.setContent(body, "text/html; charset=utf-8");

                Transport.send(message);

                System.out.println("E-mail envoyé à: " + adminEmail);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}