// SPDX-License-Identifier: MPL-2.0
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;
import ca.teamdman.sfm.client.search.nucleo.NucleoSearch;
import java.io.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class JavaOracle {
    static void verifyUtf16(String original, NucleoMatcher.Match match) {
        var expectedSpans = new ArrayList<NucleoMatcher.Span>();
        int previous = -1;
        for (int point : match.codePointPositions()) {
            if (point <= previous) throw new AssertionError("positions not strictly increasing");
            int start = original.offsetByCodePoints(0, point);
            int end = original.offsetByCodePoints(0, point + 1);
            if (!expectedSpans.isEmpty() && expectedSpans.get(expectedSpans.size() - 1).end() == start) {
                var last = expectedSpans.remove(expectedSpans.size() - 1);
                expectedSpans.add(new NucleoMatcher.Span(last.start(), end));
            } else expectedSpans.add(new NucleoMatcher.Span(start, end));
            previous = point;
        }
        if (!expectedSpans.equals(match.utf16Spans())) throw new AssertionError("wrong UTF-16 spans: " + match);
    }

    static void validatorChecks() {
        var points = java.util.List.of(1);
        verifyUtf16("a😀", new NucleoMatcher.Match(0, points, java.util.List.of(new NucleoMatcher.Span(1, 3))));
        for (var wrong : java.util.List.of(new NucleoMatcher.Span(1, 2), new NucleoMatcher.Span(2, 3),
                new NucleoMatcher.Span(1, 4), new NucleoMatcher.Span(0, 3))) {
            boolean rejected = false;
            try { verifyUtf16("a😀", new NucleoMatcher.Match(0, points, java.util.List.of(wrong))); }
            catch (AssertionError expected) { rejected = true; }
            if (!rejected) throw new AssertionError("validator accepted malformed range " + wrong);
        }
        System.out.println("UTF-16 validator: PASS (split-surrogate, wrong-start, out-of-bounds and extra-character ranges rejected)");
    }
    static String decode(String text) {
        var result = new StringBuilder();
        if (!text.isEmpty()) for (String s : text.split(",")) result.appendCodePoint(Integer.parseInt(s, 16));
        return result.toString();
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("validator-checks")) { validatorChecks(); return; }
        try (var reader = new BufferedReader(new InputStreamReader(System.in));
             var writer = new BufferedWriter(new OutputStreamWriter(System.out))) {
            for (String line; (line = reader.readLine()) != null;) {
                String[] f = line.split("\t", -1);
                var options = new NucleoMatcher.Options(NucleoMatcher.CaseMode.valueOf(f[0]),
                        NucleoMatcher.Normalization.valueOf(f[1]), NucleoMatcher.Boundaries.valueOf(f[2]),
                        Boolean.parseBoolean(f[3]));
                if (args.length > 0 && args[0].equals("rank")) {
                    var entries = new ArrayList<NucleoSearch.Entry<Integer>>();
                    for (int i = 5; i < f.length; i++) entries.add(new NucleoSearch.Entry<>(i - 5, decode(f[i])));
                    var results = new NucleoSearch<>(entries).search(decode(f[4]), options, entries.size(), () -> false);
                    writer.write(results.stream().map(r -> r.inputIndex() + ":" + r.match().score())
                            .collect(Collectors.joining(",")) + "\n");
                    continue;
                }
                var matcher = new NucleoMatcher(decode(f[4]), options);
                var candidate = new NucleoMatcher.Candidate(decode(f[5]));
                var match = matcher.match(candidate);
                int score = matcher.score(candidate);
                if (score != (match == null ? -1 : match.score())) throw new AssertionError("score-only mismatch");
                if (match == null) writer.write("-\n");
                else {
                    // Validate UTF-16 translation independently of the matcher.
                    verifyUtf16(candidate.text(), match);
                    writer.write(match.score() + "\t" + match.codePointPositions().stream().map(Object::toString)
                            .collect(Collectors.joining(",")) + "\n");
                }
            }
        }
    }
}
