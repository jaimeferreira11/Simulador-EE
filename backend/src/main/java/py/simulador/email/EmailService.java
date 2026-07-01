package py.simulador.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import py.simulador.config.AppProperties;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    public EmailService(JavaMailSender mailSender, AppProperties appProperties) {
        this.mailSender = mailSender;
        this.appProperties = appProperties;
    }

    @Async
    public void enviarInvitacion(String destinatario, String nombreJugador,
                                  String nombreEquipo, String nombreCompetencia,
                                  String codigoColor, String token) {
        String registroUrl = appProperties.baseUrl() + "/registro?token=" + token;

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0; padding:0; background-color:#F5F5F0; font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="background:#FFFFFF; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#006B3F,#004D2C); padding:32px 40px;">
                            <h1 style="margin:0; color:#FFFFFF; font-size:24px; font-weight:700; letter-spacing:1px;">SIMULADOR</h1>
                            <p style="margin:8px 0 0; color:#F47920; font-size:16px; font-weight:600;">Invitacion a competencia</p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:32px 40px;">
                            <p style="margin:0 0 20px; color:#1A1A1A; font-size:16px; line-height:1.5;">
                              Hola <strong>%s</strong>,
                            </p>
                            <p style="margin:0 0 24px; color:#4A4A4A; font-size:14px; line-height:1.6;">
                              Fuiste invitado a participar en una competencia de simulacion de negocios.
                            </p>

                            <!-- Info card -->
                            <table width="100%%" cellpadding="0" cellspacing="0" style="background:#F5F5F0; border-radius:8px; margin-bottom:28px;">
                              <tr>
                                <td style="padding:20px 24px;">
                                  <table width="100%%" cellpadding="0" cellspacing="0">
                                    <tr>
                                      <td style="padding-bottom:12px;">
                                        <span style="color:#7A7A7A; font-size:11px; text-transform:uppercase; letter-spacing:0.5px;">Competencia</span><br>
                                        <span style="color:#1A1A1A; font-size:15px; font-weight:600;">%s</span>
                                      </td>
                                    </tr>
                                    <tr>
                                      <td>
                                        <span style="color:#7A7A7A; font-size:11px; text-transform:uppercase; letter-spacing:0.5px;">Equipo</span><br>
                                        <span style="display:inline-flex; align-items:center; gap:8px;">
                                          <span style="display:inline-block; width:12px; height:12px; border-radius:50%%; background-color:%s;"></span>
                                          <span style="color:#1A1A1A; font-size:15px; font-weight:600;">%s</span>
                                        </span>
                                      </td>
                                    </tr>
                                  </table>
                                </td>
                              </tr>
                            </table>

                            <!-- CTA -->
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td align="center">
                                  <a href="%s"
                                     style="display:inline-block; background:#006B3F; color:#FFFFFF; text-decoration:none;
                                            padding:14px 40px; border-radius:8px; font-size:15px; font-weight:600;
                                            letter-spacing:0.3px;">
                                    Aceptar invitacion
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0; color:#7A7A7A; font-size:12px; line-height:1.5; text-align:center;">
                              Si no esperabas esta invitacion, podes ignorar este correo.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="padding:20px 40px; border-top:1px solid #E8E8E3; text-align:center;">
                            <p style="margin:0; color:#A0A0A0; font-size:11px;">
                              Simulador de Negocios — Paraguay
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(nombreJugador, nombreCompetencia, codigoColor, nombreEquipo,
                registroUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.mailFrom());
            helper.setTo(destinatario);
            helper.setSubject("Invitacion a competencia — " + nombreCompetencia);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email de invitacion enviado a {}", destinatario);
        } catch (MessagingException e) {
            log.error("Error al enviar email de invitacion a {}: {}", destinatario, e.getMessage());
        }
    }

    /**
     * Email de aprovisionamiento de cuenta: una cuenta recien creada por un
     * MODERADOR/ADMIN no conoce su contrasena, asi que recibe este correo con un
     * enlace para definirla (reutiliza el flujo de token de reset de contrasena).
     *
     * <p>Async + best-effort: cualquier fallo de envio se registra y se traga,
     * nunca propaga la excepcion, para no abortar la creacion del usuario.
     */
    @Async
    public void enviarSetPassword(String destinatario, String nombreCompleto, String token) {
        String setPasswordUrl = appProperties.baseUrl() + "/restablecer-password?token=" + token;

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0; padding:0; background-color:#F5F5F0; font-family:'Segoe UI',Roboto,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 20px;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="background:#FFFFFF; border-radius:12px; overflow:hidden; box-shadow:0 2px 8px rgba(0,0,0,0.08);">

                        <!-- Header -->
                        <tr>
                          <td style="background:linear-gradient(135deg,#006B3F,#004D2C); padding:32px 40px;">
                            <h1 style="margin:0; color:#FFFFFF; font-size:24px; font-weight:700; letter-spacing:1px;">SIMULADOR</h1>
                            <p style="margin:8px 0 0; color:#F47920; font-size:16px; font-weight:600;">Activa tu cuenta</p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:32px 40px;">
                            <p style="margin:0 0 20px; color:#1A1A1A; font-size:16px; line-height:1.5;">
                              Hola <strong>%s</strong>,
                            </p>
                            <p style="margin:0 0 24px; color:#4A4A4A; font-size:14px; line-height:1.6;">
                              Se creo una cuenta para vos en el Simulador de Negocios. Para empezar a usarla,
                              defini tu contrasena haciendo clic en el siguiente boton.
                            </p>

                            <!-- CTA -->
                            <table width="100%%" cellpadding="0" cellspacing="0">
                              <tr>
                                <td align="center">
                                  <a href="%s"
                                     style="display:inline-block; background:#006B3F; color:#FFFFFF; text-decoration:none;
                                            padding:14px 40px; border-radius:8px; font-size:15px; font-weight:600;
                                            letter-spacing:0.3px;">
                                    Definir mi contrasena
                                  </a>
                                </td>
                              </tr>
                            </table>

                            <p style="margin:24px 0 0; color:#7A7A7A; font-size:12px; line-height:1.5; text-align:center;">
                              Este enlace vence en 1 hora. Si no esperabas este correo, podes ignorarlo.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="padding:20px 40px; border-top:1px solid #E8E8E3; text-align:center;">
                            <p style="margin:0; color:#A0A0A0; font-size:11px;">
                              Simulador de Negocios — Paraguay
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(nombreCompleto, setPasswordUrl);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(appProperties.mailFrom());
            helper.setTo(destinatario);
            helper.setSubject("Activa tu cuenta — Simulador de Negocios");
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Email de definicion de contrasena enviado a {}", destinatario);
        } catch (Exception e) {
            log.error("Error al enviar email de definicion de contrasena a {}: {}",
                    destinatario, e.getMessage());
        }
    }
}
