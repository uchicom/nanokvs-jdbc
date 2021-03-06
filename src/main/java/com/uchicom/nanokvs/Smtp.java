// (c) 2017 uchicom
package com.uchicom.nanokvs;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Smtp implements Closeable {

	/** ロガー */
	private static final Logger logger = Logger.getLogger(Smtp.class.getCanonicalName());
	protected Properties info;

	protected String smtpHost;
	protected int smtpPort = 25;
	protected String smtpDb;
	protected Socket smtpSocket;
	protected PrintStream smtpPs;
	protected BufferedReader smtpBr;
	public Smtp(String url, Properties info) {
		if (url.startsWith(MailDriver.URL_PREFIX)) {
			String hosts = url.substring(MailDriver.URL_PREFIX.length());
			String[] hostArray = hosts.split(",");

			if (hostArray.length > 1) {
				String[] smtpDbs = hostArray[1].split("/", 2);
				if (smtpDbs.length > 1) {
					smtpDb = smtpDbs[1];
				}
				String[] smtps = smtpDbs[0].split(":", 2);
				smtpHost = smtps[0];
				if (smtps.length > 1) {
					smtpPort = Integer.parseInt(smtps[1]);
				}
			}
		}
		this.info = info;
	}
	/**
	 * 挿入処理.
	 *
	 * @param box
	 * @param json
	 */
	public int insert(String box, String json) throws SQLException {
		logger.info("insert " + box + " " + json);
		try {
			String value = null;
			if (smtpSocket == null || !smtpSocket.isConnected() || smtpSocket.isClosed()) {
				smtpSocket = ConnectionUtil.createSocket(smtpHost, smtpPort, "true".equals(info.getProperty("smtp.ssl")));
				smtpPs = new PrintStream(smtpSocket.getOutputStream());
				smtpBr = new BufferedReader(new InputStreamReader(
						smtpSocket.getInputStream()));

				value = smtpBr.readLine();
				logger.info(value);
//				ConnectionUtil.writeclf(smtpPs, "HELO " + info.getProperty("smtp.helo"));
//				value = smtpBr.readLine();
//				logger.info(value);
				ConnectionUtil.writeclf(smtpPs, "EHLO " + info.getProperty("smtp.helo"));
				value = smtpBr.readLine();
				logger.info(value);
				while (value.startsWith("250-")) {
					value = smtpBr.readLine();
					logger.info(value);
				}

				//SSL接続の場合
				if (info.containsKey("smtp.auth")) {
					switch (info.getProperty("smtp.auth")) {
					case "plain":
						ConnectionUtil.writecl(smtpPs, "AUTH PLAIN");
						String smtpUser = info.getProperty("smtp.user");
						String smtpPass = info.getProperty("smtp.pass");
						ConnectionUtil.writeclf(smtpPs,
								Base64.getEncoder()
										.encodeToString((smtpUser + "\0" + smtpUser + "\0" + smtpPass).getBytes()));

						value = smtpBr.readLine();
						logger.info(value);
						value = smtpBr.readLine();
						logger.info(value);
						break;
					}
				}
			}

			ConnectionUtil.writecl(smtpPs, "MAIL FROM: " + info.getProperty("smtp.mail_from"));
			ConnectionUtil.writecl(smtpPs, "RCPT TO: " + info.getProperty("smtp.rcpt_to"));
			ConnectionUtil.writecl(smtpPs, "DATA");
			ConnectionUtil.writecl(smtpPs, "From: " + info.getProperty("smtp.from"));
			ConnectionUtil.writecl(smtpPs, "To: " + info.getProperty("smtp.to"));
			if (smtpDb != null && !"".equals(smtpDb)) {
				ConnectionUtil.writecl(smtpPs, "Subject: " + box + " " + smtpDb);
			} else {
				ConnectionUtil.writecl(smtpPs, "Subject: " + box);
			}
			ZonedDateTime datetime = ZonedDateTime.now();
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
			ConnectionUtil.writecl(smtpPs, "Date: " + datetime.format(formatter));
			ConnectionUtil.writecl(smtpPs, "Message-ID: <" + System.currentTimeMillis() + "@" + info.getProperty("smtp.domain") + ">");
//			ConnectionUtil.writecl(smtpPs, "Content-Type: text/plain;");
//			ConnectionUtil.writecl(smtpPs, "\tcharset=\"utf-8\"");
//			ConnectionUtil.writecl(smtpPs, "Content-Transfer-Encoding: base64");//76文字で改行
			ConnectionUtil.writecl(smtpPs, "");
			for (String keyValue : json.split(",")) {
				ConnectionUtil.writecl(smtpPs, keyValue);
			}
			ConnectionUtil.writeclf(smtpPs, ".");
			value = smtpBr.readLine();
			logger.info(value);
			value = smtpBr.readLine();
			logger.info(value);
			value = smtpBr.readLine();
			logger.info(value);
			value = smtpBr.readLine();
			logger.info(value);
		} catch (IOException e) {
			throw new SQLException(e);
		} catch (Exception e) {
			throw new SQLException(e);
		}
		return 1;
	}
	/* (非 Javadoc)
	 * @see java.io.Closeable#close()
	 */
	@Override
	public void close() throws IOException {
		if (smtpSocket != null) {
			if (!smtpSocket.isOutputShutdown()) {
				ConnectionUtil.writeclf(smtpPs, "QUIT\r\n");
				logger.info("QUIT SMTP!");
			}
			smtpSocket.close();
		}
	}

	public boolean isClosed() {
		return smtpSocket == null || smtpSocket.isClosed();
	}
}
