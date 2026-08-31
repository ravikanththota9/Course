import java.net.IDN;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

public class ContactValidator {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@" +
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$"
    );

    private static final Pattern DOMAIN_PART_PATTERN =
            Pattern.compile("(?i)^(?!-)[a-z0-9-]{1,63}(?<!-)$");

    private static final Pattern TLD_PATTERN =
            Pattern.compile("(?i)^[a-z]{2,63}$");

    public static String getFlag(String databaseValue) {

        if (databaseValue == null || databaseValue.isBlank()) {
            return null;
        }

        String value = databaseValue.trim();

        // Check email first.
        if (isValidEmail(value)) {
            return "EM";
        }

        if (isValidWebsite(value)) {
            return "WEB";
        }

        // The value is neither a valid email nor a valid website.
        return null;
    }

    public static boolean isValidEmail(String value) {

        if (value == null
                || value.isBlank()
                || value.length() > 254) {
            return false;
        }

        return EMAIL_PATTERN.matcher(value).matches();
    }

    public static boolean isValidWebsite(String value) {

        if (value == null || value.isBlank()) {
            return false;
        }

        String urlToValidate = value.trim();

        /*
         * Add a temporary protocol for values such as:
         * abc.com
         * bone-joint.net
         * www.example.com
         */
        if (!urlToValidate.matches("(?i)^https?://.*")) {
            urlToValidate = "https://" + urlToValidate;
        }

        try {
            URI uri = new URI(urlToValidate);

            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                return false;
            }

            if (!scheme.equalsIgnoreCase("http")
                    && !scheme.equalsIgnoreCase("https")) {
                return false;
            }

            // Reject URLs such as https://john@abc.com.
            if (uri.getUserInfo() != null) {
                return false;
            }

            String asciiHost = IDN.toASCII(host)
                    .toLowerCase(Locale.ROOT);

            if (asciiHost.length() > 253
                    || asciiHost.startsWith(".")
                    || asciiHost.endsWith(".")
                    || !asciiHost.contains(".")) {
                return false;
            }

            String[] domainParts = asciiHost.split("\\.");

            for (String part : domainParts) {
                if (!DOMAIN_PART_PATTERN.matcher(part).matches()) {
                    return false;
                }
            }

            String topLevelDomain =
                    domainParts[domainParts.length - 1];

            return TLD_PATTERN.matcher(topLevelDomain).matches();

        } catch (Exception exception) {
            return false;
        }
    }

    public static void main(String[] args) {

        // Assume this String came from Oracle.
        String databaseValue = "bone-joint.net";

        String flag = getFlag(databaseValue);

        if (flag != null) {
            String output = databaseValue.trim() + "|" + flag;

            // Pass this value to your existing text-file writer.
            System.out.println(output);
        } else {
            System.out.println(
                    "Invalid email or website: " + databaseValue
            );
        }
    }
}
