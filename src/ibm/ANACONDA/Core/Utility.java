package ibm.ANACONDA.Core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utility {
	private static final Pattern STANDARD_URL_MATCH_PATTERN = Pattern.compile("(http://|https://){1}[\\w\\.\\-/:\\#\\?\\=\\&\\;\\%\\~\\+]+", Pattern.CASE_INSENSITIVE);

	private static final Pattern STANDARD_TICKER_MATCH_PATTERN = Pattern.compile("\\D\\d{4}\\D", Pattern.CASE_INSENSITIVE);

	private static Random rand = new Random();

	public static String[] LinkExtract(String text) {
		List<String> list = new ArrayList<String>();
		Matcher matcher = STANDARD_URL_MATCH_PATTERN.matcher(text);
		while (matcher.find()) {
			list.add(matcher.group());
		}
		return list.toArray(new String[list.size()]);
	}

	public static int[] TickExtract(String text) {
		List<String> list = new ArrayList<String>();
		Matcher matcher = STANDARD_TICKER_MATCH_PATTERN.matcher(text);
		while (matcher.find()) {
			list.add(matcher.group());
		}

		int num = list.size();
		if (num == 0) return null;

		int[] ret = new int[num];
		for (int i = 0; i < num; i++) {
			String tickStr = list.get(i).substring(1, 5);
			ret[i] = Integer.parseInt(tickStr);
		}

		return ret;
	}

	@SuppressWarnings("resource")
	public static void copy(File srcPath, File destPath) throws Exception {
		FileChannel srcChannel = null;
		FileChannel destChannel = null;

		srcChannel = new FileInputStream(srcPath).getChannel();
		destChannel = new FileOutputStream(destPath).getChannel();

		srcChannel.transferTo(0, srcChannel.size(), destChannel);

		srcChannel.close();
		destChannel.close();
	}

	public static int ComputeHash(String text, int max) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] digest = md.digest(text.getBytes());
		long temp = 0;
		temp = temp * 256 + digest[0] + 128;
		temp = temp * 256 + digest[1] + 128;
		temp = temp * 256 + digest[2] + 128;
		int ret = (int) (temp % max);
		return ret;
	}

	public static void GetFile(String addr, String filename) throws Exception {
		URL url = new URL(addr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setReadTimeout(60 * 1000);

		InputStream is = conn.getInputStream();
		FileOutputStream fos = new FileOutputStream(new File(filename));
		while (true) {
			byte[] buffer = new byte[1024];
			int len = is.read(buffer);
			if (len <= 0) break;

			fos.write(buffer, 0, len);
		}
		fos.flush();
		fos.close();
		is.close();
		conn.disconnect();
	}

	public static double ComputeRandomGaussian() {
		double X = rand.nextDouble();
		double Y = rand.nextDouble();
		double d = Math.sqrt(-2 * Math.log(X)) * Math.cos(2 * Math.PI * Y);
		return d;
	}
}
