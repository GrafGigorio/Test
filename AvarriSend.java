package ru;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.bitel.bgbilling.kernel.script.server.dev.GlobalScriptBase;
import ru.bitel.bgbilling.server.util.Setup;
import ru.bitel.common.Utils;
import ru.bitel.common.sql.ConnectionSet;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;


public class AvarriSend extends GlobalScriptBase {

    protected static final Logger log = LogManager.getLogger();
    private String encoding;
    private MimeMessage msg;
    String subject2 = "Сводка массовых авариq";
    String mailTo = "xx@xx.xx";
  //период формирования отчета
    static int dayFrom = -1;
    static int dayTo = -1;

    int close = 0, open = 0, all = 0;

    public static void main(String[] args) {
        //  run("xx@xx.xx");

//        GregorianCalendar gg_to = new GregorianCalendar();
//
//        System.out.println(gg_to.get(gg_to.DAY_OF_WEEK));

    }
    @Override
    public void execute(Setup setup, ConnectionSet set) throws Exception {
        GregorianCalendar dds = new GregorianCalendar();
        System.out.println("1"+subject2);
        //Если понедельник
        if(dds.get(dds.DAY_OF_WEEK) == 2)
        {
            subject2 = "Сводка массовых аварий за выходные (ERP)";
            dayFrom = -2;
        }
        System.out.println("1"+subject2);

        String fromAddress = setup.get("mail.from.email", (String)null);
        String fromName = setup.get("mail.from.name", "BGBilling server");
        this.encoding = setup.get("mail.encoding", "UTF-8");
        String user = setup.get("mail.smtp.user", (String)null);
        String pswd = setup.get("mail.smtp.pswd", (String)null);
        if (fromAddress != null) {
            Session session = null;
            Properties props = new Properties();
            props.setProperty("mail.smtp.host", setup.get("mail.smtp.host", (String)null));
            props.setProperty("mail.smtp.port", setup.get("mail.smtp.port", "25"));
            props.setProperty("mail.smtp.localhost", setup.get("mail.smtp.localhost", ""));
            props.setProperty("mail.debug", String.valueOf(setup.getBoolean("mail.debug", false)));
            Iterator var8 = setup.sub("mail.properties.").entrySet().iterator();

            while(var8.hasNext()) {
                Map.Entry<String, String> entry = (Map.Entry)var8.next();
                props.put(entry.getKey(), entry.getValue());
            }

            Authenticator authenticator = null;
            if (Utils.notBlankString(user) && Utils.notBlankString(pswd)) {
                authenticator = new Authenticator(user, pswd);
                props.setProperty("mail.smtp.auth", "true");
                props.setProperty("mail.smtp.submitter", authenticator.getPasswordAuthentication().getUserName());
            }

            session = Session.getInstance(props, authenticator);
            this.msg = new MimeMessage(session);

            try {
                this.msg.setFrom(new InternetAddress(fromAddress, fromName, this.encoding));
                this.msg.setSentDate(new Date());
            } catch (Exception var10) {
                log.error("error init MailMsg", var10);
            }
        }
        run(mailTo);
    }
    public void run(String to)
    {
        AvarriSend avr = new AvarriSend();

        SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd");
        GregorianCalendar gg_from = new GregorianCalendar();
        GregorianCalendar gg_to = new GregorianCalendar();

        gg_from.roll(gg_from.DAY_OF_YEAR, dayFrom); //<<<<< Число дней назад с текущей даты сейчас -1 / за вчера
        gg_to.roll(gg_to.DAY_OF_YEAR,dayTo);

        String datetosql_from = formater.format(gg_from.getTime()) + " 00:00:00";
        String datetosql_to = formater.format(gg_to.getTime()) + " 23:59:59";

        //Получаем аварии из базы
        List<Map<String, String>> avarii = new ArrayList<>();
        try {
            avarii = getAvr(datetosql_from,datetosql_to);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        String temp = avr.replaicment(avarii,datetosql_from,datetosql_to);

        avr.send(to,temp, subject2);
        System.out.println(datetosql_from);
        System.out.println(datetosql_to);
        System.out.println("sended");
    }
    public String replaicment(List<Map<String, String>> avr, String dateFrom, String dateTo)
    {
        int opened = 0;
        int cloased = 0;

        StringBuilder top = new StringBuilder();
        StringBuilder bot = new StringBuilder();

        String header = readFile("avariiHtml/avariiHeader.html");
        String template = readFile("avariiHtml/avariiSend.html");
        String process = readFile("avariiHtml/avariiProcess.html");

//        String header = readFile("avariiHeader.html");
//        String template = readFile("avariiSend.html");
//        String process = readFile("avariiProcess.html");

        SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss:SSS");
        GregorianCalendar gg = new GregorianCalendar();

        if(avr.size() > 0) {
            for (Map<String, String> par : avr) {
                String s = par.get("close_dt");

                String dq = process;
                int live = (int)Double.parseDouble(par.get("timeInWork_minutes") != null ? par.get("timeInWork_minutes") : "-1" );
                int sla = (int)Double.parseDouble(par.get("sla_179") != null ? par.get("sla_179") : "-1");

                dq = dq.replace("{PROCESS_ID}", par.get("id"));
                dq = dq.replace("{OPERATOR}", par.get("operator_68"));
                dq = dq.replace("{OPISANIE}", par.get("description"));
                dq = dq.replace("{CITY}", par.get("city_84"));
                dq = dq.replace("{ZONA_VLIANIA}", par.get("zona_163_val") != null ? par.get("zona_163_val") : "Не указанно");
                dq = dq.replace("{CREATE_DT}", par.get("create_dt") );
                dq = dq.replace("{CLOSE_DT}", par.get("close_dt")!= null ? par.get("close_dt") : "Еще в работе");
                dq = dq.replace("{TRABL_RSHENIE}", par.get("reshenieProblem_49") != null ? par.get("reshenieProblem_49") : "Не указанно");
                dq = dq.replace("{COMMENT_VIEZD}", par.get("commentViezd_78") != null ? par.get("commentViezd_78") : "Не указан");
                dq = dq.replace("{PRICHITA_TT}", par.get("prichina_164_val") + " ["+ par.get("prichina_164_comment")+"]");
                dq = dq.replace("{SLA2}", par.get("sla_179") != null ? par.get("sla_179") :"Не указанн");
                dq = dq.replace("{TIME_TO_LIVE}", par.get("timeInWork_minutes") != null ? par.get("timeInWork_minutes") : "В работе" );
                if(live == -1)
                    dq = dq.replace("{SLA_ACCEPT}", "В работе");
                else
                    dq = dq.replace("{SLA_ACCEPT}", sla - live >= 0 ? " Уложились! ": " Не уложились! ");
                dq = dq.replace("{TIME_LOSS}", sla - live >= 0 ? 0+"" : ((sla - live) * -1)+" минуты");

                if (s == null) {
                    top.append(dq);
                    ++opened;
                } else {
                    bot.append(dq);
                    ++cloased;
                }
            }
        }

        header = header.replace("{DATE_PATTERN}",dateFrom + " по " + dateTo);
        header = header.replace("{DATE_OTCHET}",formater.format(gg.getTime()));
        header = header.replace("{PROCESS_COUNT}","   " +avr.size()+"   ");
        header = header.replace("{PROCESS_OPENED}","   " +opened+"   ");
        header = header.replace("{PROCESS_CLOSED}","   " + cloased+"   ");

        top.append(bot);

        template = template.replace("{PROCESS_BLOCK}", top);
        template = template.replace("{HEADER_BLOCK}", header);

//        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
//        System.out.println(template);
//        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>");
        return template;
    }

    public String readFile(String filename)
    {
        StringBuilder ret = new StringBuilder();
        File file = new File(filename);
        FileReader fr = null;
        try {
            fr = new FileReader(file);

            int content;
            while ((content = fr.read()) != -1) {
                ret.append((char) content);
            }
        } catch (IOException e ) {
            throw new RuntimeException(e);
        }
        return ret.toString();
    }

    public void send(String from, String body, String sub)
    {
        //Для запуска вне биллинга
        String fromAddress = from;

        String user = "xx@xx.ru";
        String pswd = "xx";

        String host = "smtp.yandex.com";
        Properties props = System.getProperties();

        props.put("mail.smtp.host", host);
        props.put("mail.encoding", "UTF-8");

        props.put("mail.smtp.user", user);
        props.put("mail.smtp.password", pswd);
        props.put("mail.smtp.port", "465");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.trust", host);
        props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        //
        Session session =
                Session.getInstance(props,
                        new javax.mail.Authenticator(){
                            protected PasswordAuthentication getPasswordAuthentication() {
                                return new PasswordAuthentication(
                                        "xx@xx.xx", "pass");// Specify the Username and the PassWord
                            }
                        });
        try {
            if(msg == null)
            {
                msg = new MimeMessage(session);
            }

            msg.setFrom(new InternetAddress(user));

            String[] mails = fromAddress.split(",");
            if(mails.length > 1)
                for (String mail : mails)
                    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(mail));
            else
                msg.addRecipient(Message.RecipientType.TO, new InternetAddress(fromAddress));

            msg.setSubject(sub);
            msg.setContent(body, "text/html; charset=UTF-8");
            Transport.send(msg);
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }

//Список аварий
    public List<Map<String, String>> getAvr(String datetosql_from, String datetosql_to) throws SQLException {
        return processes;
    }

    private static class Authenticator extends javax.mail.Authenticator {
        private PasswordAuthentication authentication;

        public Authenticator(String user, String password) {
            this.authentication = new PasswordAuthentication(user, password);
        }

        protected PasswordAuthentication getPasswordAuthentication() {
            return this.authentication;
        }
    }
}
