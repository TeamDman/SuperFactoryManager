// SPDX-License-Identifier: MPL-2.0
import ca.teamdman.sfm.client.search.nucleo.NucleoMatcher;
import ca.teamdman.sfm.client.search.nucleo.NucleoSearch;
import java.io.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class JavaOracle {
    static String decode(String text) {
        var result = new StringBuilder();
        if (!text.isEmpty()) for (String s : text.split(",")) result.appendCodePoint(Integer.parseInt(s, 16));
        return result.toString();
    }
    public static void main(String[] args) throws Exception {
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
                    String original = candidate.text();
                    var starts = match.codePointPositions().stream()
                            .map(i -> original.offsetByCodePoints(0, i)).collect(Collectors.toSet());
                    int count = 0;
                    for (var span : match.utf16Spans()) {
                        for (int i = span.start(); i < span.end(); i += Character.charCount(original.codePointAt(i))) {
                            if (!starts.contains(i)) throw new AssertionError("wrong UTF-16 span");
                            count++;
                        }
                    }
                    if (count != starts.size()) throw new AssertionError("missing UTF-16 span: " + line + " " + match);
                    writer.write(match.score() + "\t" + match.codePointPositions().stream().map(Object::toString)
                            .collect(Collectors.joining(",")) + "\n");
                }
            }
        }
    }
}
