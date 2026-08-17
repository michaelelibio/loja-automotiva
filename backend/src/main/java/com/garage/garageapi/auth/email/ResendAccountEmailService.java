package com.garage.garageapi.auth.email;

import com.garage.garageapi.shared.email.ResendEmailClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@ConditionalOnProperty(name = "app.account.email.mode", havingValue = "resend")
public class ResendAccountEmailService implements AccountEmailService {
    private final ResendEmailClient client;

    @Autowired
    public ResendAccountEmailService(ResendEmailClient client) { this.client = client; }

    ResendAccountEmailService(RestClient restClient, String apiKey, String from) {
        this(new ResendEmailClient(restClient, apiKey, from));
    }

    @Override public void sendVerificationEmail(String email, String url) {
        send(email, "Confirme seu e-mail — GARAGE", "Confirme seu e-mail", "Confirmar e-mail", url,
                "Recebemos um cadastro na GARAGE usando este endereço de e-mail.");
    }

    @Override public void sendPasswordResetEmail(String email, String url) {
        send(email, "Redefinição de senha — GARAGE", "Redefina sua senha", "Redefinir senha", url,
                "Recebemos uma solicitação para redefinir a senha da sua conta GARAGE.");
    }

    private void send(String email, String subject, String title, String button, String url, String intro) {
        String safe = escape(url);
        String html = """
                <!doctype html><html lang="pt-BR"><body style="margin:0;background:#111827;font-family:Arial,sans-serif;color:#111827">
                <div style="max-width:600px;margin:0 auto;padding:32px 20px"><div style="background:#fff;border-radius:12px;padding:32px">
                <h1 style="margin:0 0 20px;color:#dc2626">GARAGE</h1><h2>%s</h2><p>%s</p>
                <p style="margin:28px 0"><a href="%s" style="background:#dc2626;color:#fff;text-decoration:none;padding:13px 22px;border-radius:8px;display:inline-block">%s</a></p>
                <p>Se o botão não funcionar, copie este link:</p><p style="word-break:break-all"><a href="%s">%s</a></p>
                <p style="color:#6b7280;font-size:14px">Se você não solicitou esta ação, ignore este e-mail.</p>
                </div></div></body></html>
                """.formatted(title, intro, safe, button, safe, safe);
        String text = title + "\n\n" + intro + "\n\nAcesse: " + url
                + "\n\nSe você não solicitou esta ação, ignore este e-mail.";
        client.send(email, subject, html, text);
    }

    private String escape(String value) { return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;"); }
}
