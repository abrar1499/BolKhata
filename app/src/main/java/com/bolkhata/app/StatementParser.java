package com.bolkhata.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a bank statement's text without AI, for any bank whose statement lists each transaction with a date and
 * amounts. It works from the statement's own structure and checks itself:
 *
 * - every line that starts with a date begins a transaction; the lines under it (and a "type" line just above it,
 *   like "POS purchase" or "Internal Transfer") are its description;
 * - when the statement has a running balance, each transaction's amount and direction come from how the balance
 *   moved, so a debit is never read as a credit and no line goes missing without it showing;
 * - printed totals (opening / closing balance, totals and counts of deposits and withdrawals) are compared too.
 *
 * {@link Result#ok} is true only when the reading checks out; otherwise the app asks the AI instead.
 */
public final class StatementParser {

    private StatementParser() {}

    public static final class Row {
        public String date = "";      // yyyy-MM-dd
        public String time = "";      // HH:mm
        public String text = "";      // the transaction's description, cleaned
        public double amount;
        public boolean in;            // money came in
        public double balance = Double.NaN;
        public boolean checked;       // amount confirmed by the running balance
        // What the app saves:
        public String kind = "expense";   // expense | income
        public String item = "";
        public String note = "";
        public String category = "OTHER"; // a Cat name
    }

    public static final class Result {
        public final List<Row> rows = new ArrayList<>();
        public boolean ok;
        public String check = "";
        public String why = "";
    }

    // ---------- text helpers ----------

    /** Direction marks around Arabic text; the Arabic itself is kept (names like "مؤسسة افاق طويق"). */
    private static final Pattern MARKS = Pattern.compile("[\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069\\uFEFF]+");
    private static final Pattern MONEY = Pattern.compile(
            "(?<![\\d.,/:])(-)?((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})(?![\\d/])\\s*(?:SAR|SR|AED|PKR|QAR|KWD|OMR|BHD|INR|USD|EUR|GBP)?\\s*(CR|DR|Cr|Dr|cr|dr)?(?![A-Za-z])");
    private static final Pattern YMD = Pattern.compile("(?<!\\d)(20\\d{2})[/.-](\\d{1,2})[/.-](\\d{1,2})(?!\\d)");
    private static final Pattern DMY = Pattern.compile("(?<!\\d)(\\d{1,2})[/.-](\\d{1,2})[/.-](20\\d{2}|\\d{2})(?![\\d/])");
    private static final Pattern DMONY = Pattern.compile(
            "(?<!\\d)(\\d{1,2})[ -]?(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[ ,-]*(20\\d{2}|\\d{2})(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME = Pattern.compile("(?:Time:\\s*)?(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?::[0-5]\\d)?(?!\\d)");
    private static final String[] MONTHS = {"jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"};

    private static String clean(String line) {
        // NFKC turns Arabic "presentation forms" from PDFs back into normal letters.
        String n = java.text.Normalizer.normalize(line, java.text.Normalizer.Form.NFKC);
        return MARKS.matcher(n).replaceAll(" ").replaceAll("\\s+", " ").trim();
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    /** A date found in a line: where it is and its parts. */
    private static final class DateHit {
        int start, end, a, b, c; // a/b/c as printed
        int kind;                // 0 = y-m-d, 1 = d/m/y (or m/d/y), 2 = d-Mon-y
    }

    private static DateHit findDate(String s) {
        DateHit best = null;
        Matcher m = YMD.matcher(s);
        if (m.find()) {
            best = new DateHit();
            best.start = m.start(); best.end = m.end(); best.kind = 0;
            best.a = Integer.parseInt(m.group(1)); best.b = Integer.parseInt(m.group(2)); best.c = Integer.parseInt(m.group(3));
        }
        m = DMONY.matcher(s);
        if (m.find() && (best == null || m.start() < best.start)) {
            best = new DateHit();
            best.start = m.start(); best.end = m.end(); best.kind = 2;
            best.a = Integer.parseInt(m.group(1));
            best.b = monthOf(m.group(2));
            best.c = year(m.group(3));
        }
        m = DMY.matcher(s);
        if (m.find() && (best == null || m.start() < best.start)) {
            best = new DateHit();
            best.start = m.start(); best.end = m.end(); best.kind = 1;
            best.a = Integer.parseInt(m.group(1)); best.b = Integer.parseInt(m.group(2)); best.c = year(m.group(3));
        }
        return best;
    }

    private static int monthOf(String s) {
        String k = s.toLowerCase(Locale.US).substring(0, 3);
        for (int i = 0; i < 12; i++) if (MONTHS[i].equals(k)) return i + 1;
        return 0;
    }

    private static int year(String s) {
        int y = Integer.parseInt(s);
        return y < 100 ? 2000 + y : y;
    }

    private static int countDates(String s) {
        int n = 0;
        for (Pattern p : new Pattern[]{YMD, DMY, DMONY}) {
            Matcher m = p.matcher(s);
            while (m.find()) n++;
        }
        return n;
    }

    private static boolean noise(String s) {
        String l = s.toLowerCase(Locale.US);
        return l.isEmpty() || l.matches("\\d{1,3}") || l.matches("page \\d+( of \\d+)?") || l.startsWith("ref. no")
                || (l.contains("date") && (l.contains("debit") || l.contains("credit") || l.contains("balance") || l.contains("description") || l.contains("details")))
                || l.startsWith("click to validate");
    }

    /** A line that names a transaction's type and sits just above its date line ("POS purchase mada pay"). */
    private static final Pattern TYPE_WORDS = Pattern.compile(
            "(?i)\\b(purchase|transfer|payment|withdrawal|deposit|charges?|salary|payroll|fee|refund|reversal|pos|atm|mada|transaction|credit|debit|remittance|sadad|bill|cash|apple pay|online)\\b");

    private static double summary(String text, String labels) {
        Matcher m = Pattern.compile("(?i)(?:" + labels + ")[^\\d\\n]{0,40}((?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2})").matcher(text);
        return m.find() ? num(m.group(1)) : Double.NaN;
    }

    private static int summaryCount(String text, String labels) {
        Matcher m = Pattern.compile("(?i)(?:" + labels + ")[^\\d\\n]{0,40}(\\d{1,5})(?![\\d.,])").matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    // ---------- reading ----------

    public static Result parse(String rawText) {
        Result res = new Result();
        if (rawText == null || rawText.trim().isEmpty()) { res.why = "no text in the file"; return res; }
        String flat = clean(rawText.replace('\n', ' '));
        double opening = summary(flat, "opening balance|balance brought forward|previous balance|beginning balance");
        double closing = summary(flat, "closing balance|ending balance|balance carried forward");
        double totalIn = summary(flat, "total deposits|total credits|total credit amount|total money in");
        double totalOut = summary(flat, "total withdrawals|total debits|total debit amount|total money out");
        int countIn = summaryCount(flat, "number of deposits|no\\. of deposits|number of credits|no\\. of credits");
        int countOut = summaryCount(flat, "number of withdrawals|no\\. of withdrawals|number of debits|no\\. of debits");

        // 1. Lines, without Arabic and page furniture.
        List<String> lines = new ArrayList<>();
        for (String l : rawText.split("\\r?\\n")) {
            String c = clean(l);
            if (!noise(c)) lines.add(c);
        }

        // 2. Transactions: a date near the start of a line (and not a "from - to" period) begins one.
        List<List<String>> recs = new ArrayList<>();
        List<DateHit> hits = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            DateHit d = findDate(l);
            boolean starts = d != null && d.start <= 3 && countDates(l) == 1;
            if (starts) {
                List<String> rec = new ArrayList<>(pending);
                pending.clear();
                rec.add(l);
                recs.add(rec);
                hits.add(d);
                continue;
            }
            // A type line right above the next date line belongs to that next transaction.
            boolean nextIsDate = false;
            if (i + 1 < lines.size()) {
                DateHit nd = findDate(lines.get(i + 1));
                nextIsDate = nd != null && nd.start <= 3 && countDates(lines.get(i + 1)) == 1;
            }
            if (nextIsDate && !MONEY.matcher(l).find() && TYPE_WORDS.matcher(l).find()) {
                pending.add(l);
            } else if (!recs.isEmpty()) {
                recs.get(recs.size() - 1).add(l);
            }
        }
        if (recs.isEmpty()) { res.why = "no dated lines"; return res; }

        // 3. Day/month order for the whole file (31/10 means day first; 10/31 means month first).
        boolean monthFirst = false;
        for (DateHit d : hits) if (d.kind == 1 && d.b > 12 && d.a <= 12) monthFirst = true;

        // 4. Each transaction's amounts, words and date.
        List<Row> rows = new ArrayList<>();
        List<List<double[]>> amounts = new ArrayList<>();
        for (int r = 0; r < recs.size(); r++) {
            String joined = String.join(" ", recs.get(r));
            DateHit d = hits.get(r);
            int y, mo, da;
            if (d.kind == 0) { y = d.a; mo = d.b; da = d.c; }
            else if (d.kind == 2) { y = d.c; mo = d.b; da = d.a; }
            else if (monthFirst) { y = d.c; mo = d.a; da = d.b; }
            else { y = d.c; mo = d.b; da = d.a; }
            if (mo < 1 || mo > 12 || da < 1 || da > 31) continue;
            Row row = new Row();
            row.date = String.format(Locale.US, "%04d-%02d-%02d", y, mo, da);
            String rest = joined;
            // Amounts: value, sign (-1 minus/DR, +1 CR, 0 none).
            List<double[]> found = new ArrayList<>();
            Matcher mm = MONEY.matcher(rest);
            StringBuffer sb = new StringBuffer();
            while (mm.find()) {
                double v = num(mm.group(2));
                double sign = mm.group(1) != null ? -1 : 0;
                if (mm.group(3) != null) sign = mm.group(3).toUpperCase(Locale.US).equals("CR") ? 1 : -1;
                found.add(new double[]{v, sign});
                mm.appendReplacement(sb, " ");
            }
            mm.appendTail(sb);
            if (found.isEmpty()) continue;
            rest = sb.toString();
            Matcher tm = TIME.matcher(rest);
            if (tm.find()) row.time = String.format(Locale.US, "%02d:%s", Integer.parseInt(tm.group(1)), tm.group(2));
            rest = YMD.matcher(rest).replaceAll(" ");
            rest = DMONY.matcher(rest).replaceAll(" ");
            rest = DMY.matcher(rest).replaceAll(" ");
            rest = rest.replaceAll("(?i)time:\\s*[0-9:]+,?", " ").replaceAll("(?i)\\bnotes?:", " ")
                    .replaceAll("\\(\\d{6,}-\\d{6,}\\)", " ").replaceAll("\\b(SAR|SR)\\b", " ")
                    .replaceAll("\\s+", " ").trim();
            row.text = rest;
            rows.add(row);
            amounts.add(found);
        }
        if (rows.isEmpty()) { res.why = "no amounts"; return res; }

        // 5. Oldest first, so the running balance can be followed.
        boolean newestFirst = rows.size() > 1 && rows.get(0).date.compareTo(rows.get(rows.size() - 1).date) > 0;
        if (newestFirst) { Collections.reverse(rows); Collections.reverse(amounts); }

        // 6. Amount and direction: from the running balance when there is one.
        boolean hasBalance = true;
        for (List<double[]> f : amounts) if (f.size() < 2) { hasBalance = false; break; }
        int checkedCount = 0;
        double prev = opening;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            List<double[]> f = amounts.get(i);
            if (hasBalance) {
                row.balance = f.get(f.size() - 1)[0] * (f.get(f.size() - 1)[1] < 0 ? -1 : 1);
                if (Double.isNaN(prev) && i + 1 < rows.size()) {
                    // First line without an opening balance: the second line tells which way the first one went.
                    prev = Double.NaN;
                }
                List<double[]> cands = new ArrayList<>(f.subList(0, f.size() - 1));
                if (!Double.isNaN(prev)) {
                    double diff = row.balance - prev;
                    double[] pick = null;
                    for (double[] c : cands) if (c[0] > 0 && Math.abs(c[0] - Math.abs(diff)) < 0.011) { pick = c; break; }
                    if (pick != null) {
                        row.amount = pick[0];
                        row.in = diff > 0;
                        row.checked = true;
                        checkedCount++;
                    } else {
                        row.amount = firstNonZero(cands);
                        row.in = guessIn(cands, row.text);
                    }
                } else {
                    row.amount = firstNonZero(cands);
                    row.in = guessIn(cands, row.text);
                }
                prev = row.balance;
            } else {
                double[] c = f.get(0);
                row.amount = c[0];
                row.in = c[1] > 0 || (c[1] == 0 && inWords(row.text));
            }
        }
        // The first line can be confirmed backwards from the second when there was no opening balance.
        if (hasBalance && Double.isNaN(opening) && rows.size() > 1 && !rows.get(0).checked) {
            Row a = rows.get(0);
            List<double[]> f = amounts.get(0);
            for (double[] c : f.subList(0, f.size() - 1)) {
                if (c[0] > 0) {
                    a.amount = c[0];
                    break;
                }
            }
        }
        rows.removeIf(r -> r.amount <= 0);

        // 7. What each line is.
        for (Row row : rows) describe(row);

        // 8. Checks.
        int inN = 0, outN = 0;
        double inSum = 0, outSum = 0;
        for (Row r : rows) { if (r.in) { inN++; inSum += r.amount; } else { outN++; outSum += r.amount; } }
        boolean totalsKnown = !Double.isNaN(totalIn) || !Double.isNaN(totalOut) || countIn >= 0 || countOut >= 0;
        boolean totalsMatch = (Double.isNaN(totalIn) || Math.abs(totalIn - inSum) < 0.6)
                && (Double.isNaN(totalOut) || Math.abs(totalOut - outSum) < 0.6)
                && (countIn < 0 || countIn == inN) && (countOut < 0 || countOut == outN);
        boolean closingMatch = Double.isNaN(closing) || !hasBalance || rows.isEmpty()
                || Math.abs(rows.get(rows.size() - 1).balance - closing) < 0.011;
        int checkable = hasBalance ? (Double.isNaN(opening) ? rows.size() - 1 : rows.size()) : 0;
        boolean chainOk = hasBalance && checkable > 0 && checkedCount >= checkable;

        res.rows.addAll(rows);
        StringBuilder msg = new StringBuilder();
        msg.append(String.format(Locale.US, "Read on the phone (no AI): %d transactions, money in %s (%d), money out %s (%d).",
                rows.size(), money(inSum), inN, money(outSum), outN));
        if (chainOk) msg.append("\n✓ Every line matches the running balance.");
        else if (hasBalance) msg.append(String.format(Locale.US, "\n⚠ %d of %d lines match the running balance.", checkedCount, checkable));
        if (totalsKnown) msg.append(totalsMatch ? "\n✓ Matches the statement's totals." : "\n⚠ Doesn't match the statement's totals.");
        if (!Double.isNaN(closing) && hasBalance) msg.append(closingMatch ? "\n✓ Closing balance matches." : "\n⚠ Closing balance doesn't match.");
        res.check = msg.toString();
        // Trusted only when the file proves it: the whole balance chain, or the printed totals.
        res.ok = rows.size() > 0 && ((chainOk && closingMatch && (!totalsKnown || totalsMatch)) || (!hasBalance && totalsKnown && totalsMatch));
        if (!res.ok) res.why = chainOk ? "totals don't match" : (hasBalance ? "balance doesn't add up" : "no balance or totals to check against");
        return res;
    }

    private static String money(double v) {
        return String.format(Locale.US, "%,.2f", v).replace(".00", "");
    }

    private static double firstNonZero(List<double[]> c) {
        for (double[] x : c) if (x[0] > 0) return x[0];
        return 0;
    }

    private static boolean guessIn(List<double[]> cands, String text) {
        for (double[] c : cands) if (c[1] != 0 && c[0] > 0) return c[1] > 0;
        // Debit, credit, balance: a non-zero second column is a credit.
        if (cands.size() >= 2 && cands.get(0)[0] == 0 && cands.get(1)[0] > 0) return true;
        if (cands.size() >= 2 && cands.get(0)[0] > 0) return false;
        return inWords(text);
    }

    private static boolean inWords(String t) {
        return Pattern.compile("(?i)\\b(inward|deposit|credit(ed)?|received|refund|reversal|salary|payroll|from)\\b").matcher(t).find();
    }

    // ---------- what a line is ----------

    private static final Pattern FEE = Pattern.compile("(?i)\\b(charges?|fees?|vat|commission)\\b");
    private static final Pattern TRANSFER = Pattern.compile("(?i)(transfer|\\bips\\b|sarie|swift|remittance|toacct|fromacct|\\btrf\\b|\\bstc pay\\b|urpay)");
    private static final Pattern SALARY = Pattern.compile("(?i)\\b(salary|payroll|wages)\\b");
    private static final Pattern ATM = Pattern.compile("(?i)\\b(atm|cash withdrawal|cash deposit)\\b");
    private static final Pattern REFUND = Pattern.compile("(?i)\\b(refund|reversal|returned)\\b");

    private static void describe(Row r) {
        String t = r.text;
        r.kind = r.in ? "income" : "expense";
        if (FEE.matcher(t).find()) {
            String name = personIn(t);
            boolean tr = TRANSFER.matcher(t).find();
            r.item = tr ? "Transfer fee" : "Bank fee";
            r.note = tr && !name.isEmpty() ? "Fee: transfer to " + name : "Bank charges";
            r.category = "FEES";
            return;
        }
        if (SALARY.matcher(t).find() && r.in) {
            r.item = "Salary";
            String from = merchant(t.replaceAll("(?i)salary|payroll|wages|credit|transfer|inward|ips|sarie", " "));
            r.note = from.isEmpty() ? "Salary" : "Salary from " + from;
            r.category = "TANKHWAH";
            return;
        }
        if (TRANSFER.matcher(t).find()) {
            String name = personIn(t);
            r.item = "Transfer";
            r.note = name.isEmpty() ? (r.in ? "Transfer in" : "Transfer out") : (r.in ? "From " : "To ") + name;
            r.category = r.in ? "TANKHWAH" : "OTHER";
            return;
        }
        if (ATM.matcher(t).find() || Pattern.compile("(?i)cash deposit").matcher(t).find()) {
            r.item = r.in ? "Cash deposit" : "Cash withdrawal";
            r.note = "ATM";
            r.category = r.in ? "TANKHWAH" : "OTHER";
            return;
        }
        if (REFUND.matcher(t).find() && r.in) {
            String m = merchant(t.replaceAll("(?i)refund|reversal|returned", " "));
            r.item = "Refund";
            r.note = m.isEmpty() ? "Refund" : "Refund from " + m;
            r.category = "TANKHWAH";
            return;
        }
        // A purchase: shop name and city.
        String body = t.replaceAll("(?i)^.*?\\b(purchase from|purchase at|payment to|paid to)\\b", " ")
                .replaceAll("(?i)\\b(pos|purchase|mada|pay|apple pay|online|domestic|international|card|debit|credit|transaction|payment)\\b", " ")
                .replaceAll("[()]", " ").replaceAll("\\s+", " ").trim();
        String[] parts = body.split("\\s*,\\s*");
        String shop = merchant(parts.length > 0 ? parts[0] : body);
        String city = parts.length > 1 ? title(parts[1].replaceAll("[^\\p{L} ]", " ").trim()) : "";
        if (city.equalsIgnoreCase("sa") || city.equalsIgnoreCase("ksa")) city = "";
        if (shop.isEmpty()) shop = r.in ? "Money in" : "Payment";
        r.item = shop;
        r.note = city.isEmpty() ? shop : shop + ", " + city;
        r.category = r.in ? "TANKHWAH" : category(t);
    }

    /** A person's name in a transfer line: ".../WALEED ALI AHMED", "TOZUBAIR-", "to Ahmed Khan". */
    private static String personIn(String t) {
        Matcher m = Pattern.compile("(?i)(?:TOACCT|FROMACCT|FRACCT)/\\d*(?:TO|FROM|FR)\\s*(\\p{L}[\\p{L} .]{1,40})").matcher(t);
        if (m.find()) {
            String n = m.group(1).replaceAll("[ .-]+$", "").trim();
            if (n.length() >= 2 && !n.equalsIgnoreCase("to") && !n.equalsIgnoreCase("from")) return title(n);
        }
        m = Pattern.compile("\\d{6,}/\\s*(\\p{L}[\\p{L} .]{1,50})").matcher(t);
        if (m.find()) return title(m.group(1).trim());
        m = Pattern.compile("(?i)\\b(?:to|from|beneficiary|sender)[: ]+(\\p{L}[\\p{L} .]{2,40})").matcher(t);
        if (m.find()) {
            String n = m.group(1).replaceAll("(?i)\\b(account|acct|bank|transfer|ips|sarie|credit|debit)\\b.*$", "").trim();
            if (n.length() >= 3) return title(n);
        }
        return "";
    }

    private static String merchant(String s) {
        String n = s.replaceAll("[^\\p{L}&' ]", " ")
                .replaceAll("(?i)\\b(co|ltd|llc|est|company|corp|inc|establishment|trading|for trading)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        String[] w = n.split(" ");
        StringBuilder out = new StringBuilder();
        for (String x : w) {
            if (x.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(x);
            if (out.length() > 28) break;
        }
        return title(out.toString());
    }

    private static String title(String s) {
        StringBuilder b = new StringBuilder();
        // Banks often join names: "MUHAMMADIMRAN" -> "Muhammad Imran".
        s = s.replaceAll("(?i)\\b(muhammad|mohammed|mohammad|muhammed|mohamed)(?=[a-z]{3,})", "$1 ");
        for (String w : s.trim().split("\\s+")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            String lw = w.toLowerCase(Locale.US);
            b.append(Character.toUpperCase(lw.charAt(0))).append(lw.substring(1));
        }
        return b.toString();
    }

    private static String category(String t) {
        String l = t.toLowerCase(Locale.US);
        String[][] map = {
                {"TRANSPORT", "sasco", "aldrees", "petromin", "naft", "station", "petrol", "fuel", "gas ", "uber", "careem", "jeeny", "bolt", "car wash", "tyre", "tire", "parking", "saptco", "railway", "mhtt", "mahatta", "محطة", "وقود"},
                {"SEHAT", "pharma", "pharmacy", "nahdi", "dawaa", "whites", "clinic", "hospital", "medical", "dental", "lab ", "sydlyt", "صيدلية", "مستشفى"},
                {"MOBILE", "stc", "mobily", "zain", "virgin", "lebara", "salam", "internet", "recharge"},
                {"PERSONAL", "barber", "salon", "laundry", "gym", "fitness", "hlaq", "hallaq", "halaq", "mghsl", "maghsala"},
                {"BIJLI", "electricity", "sec ", "water", "sadad", "bill"},
                {"FEES", "absher", "muqeem", "iqama", "jawazat", "traffic", "moi ", "government", "visa"},
                {"KHANA", "restaurant", "mtam", "matam", "cafe", "coffee", "kafe", "bakery", "sweets", "food", "kitchen", "broast", "shawarma",
                        "pizza", "burger", "grill", "baik", "kudu", "herfy", "mcdonald", "kfc", "hardee", "starbucks", "dunkin", "tea", "juice",
                        "panda", "othaim", "tamimi", "lulu", "danube", "bindawood", "carrefour", "hyper", "supermarket", "market", "grocery",
                        "baqala", "bakala", "meat", "chicken", "fish", "fruit", "vegetable", "dates", "zahrat", "talabat", "jahez", "hungerstation",
                        "mrsool", "keeta", "ninja", "mqha", "mqhy", "maqha", "qahwa", "kofi", "karak", "shai", "matbakh", "mtbkh", "mkhbz",
                        "makhbaz", "bufia", "bofia", "halwyat", "hlwyat", "mshwyat", "mandi", "mndy", "tmwinat", "tamween", "bqala",
                        "مطعم", "مقهى", "كافيه", "مخبز", "حلويات", "بقالة", "تموينات", "سوبر"},
                {"SHOPPING", "amazon", "noon", "shein", "namshi", "jarir", "extra", "ikea", "centrepoint", "max ", "mall", "store", "shop"}
        };
        for (String[] row : map) for (int i = 1; i < row.length; i++) if (l.contains(row[i])) return row[0];
        return "OTHER";
    }
}
