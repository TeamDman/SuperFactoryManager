// SPDX-License-Identifier: MPL-2.0
// Oracle adapter for helix-editor/nucleo 8c16d47cdfa9607d3e44df5f81c635c6f43c65ee.
use nucleo_matcher::{chars, pattern::{Atom, AtomKind, CaseMatching, Normalization}, Config, Matcher, Utf32Str};
use std::io::{self, BufRead};

fn decode(s: &str) -> String {
    if s.is_empty() { return String::new(); }
    s.split(',').map(|s| char::from_u32(u32::from_str_radix(s, 16).unwrap()).unwrap()).collect()
}

fn main() {
    if std::env::args().nth(1).as_deref() == Some("bench") {
        let text = std::fs::read_to_string(std::env::args().nth(2).unwrap()).unwrap();
        let data: Vec<_> = text.lines().collect();
        let mut matcher = Matcher::new(Config::DEFAULT);
        println!("count,query,mode,first_ms,p50_ms,p95_ms,allocated_bytes,checksum");
        for count in [1000, 10000, 50000, 100000] {
            for query in ["", "i", "iron", "ii", "m:ii", "cog", "copper_plate", "zzzz_missing"] {
                let atom = Atom::new(query, CaseMatching::Smart, Normalization::Smart, AtomKind::Fuzzy, false);
                let mut run = || -> i64 {
                    let mut sum = 0;
                    for h in &data[..count] {
                        sum += atom.score(Utf32Str::Ascii(h.as_bytes()), &mut matcher).map_or(-1, i64::from);
                    }
                    std::hint::black_box(sum)
                };
                let now = std::time::Instant::now();
                let checksum = run();
                let cold = now.elapsed().as_secs_f64() * 1000.0;
                for _ in 0..6 { run(); }
                let mut times = Vec::new();
                for _ in 0..21 {
                    let now = std::time::Instant::now(); run();
                    times.push(now.elapsed().as_secs_f64() * 1000.0);
                }
                times.sort_by(f64::total_cmp);
                println!("{count},{query},rust-score,{cold:.3},{:.3},{:.3},-1,{checksum}", times[10], times[19]);
            }
        }
        return;
    }
    if std::env::args().nth(1).as_deref() == Some("tables") {
        println!("VERSION\t{:?}", char::UNICODE_VERSION);
        let mut last_class = 255;
        for n in 0..=0x10ffff {
            let Some(c) = char::from_u32(n) else { continue; };
            let fold = chars::to_lower_case(c);
            let norm = chars::normalize(c);
            if fold != c { println!("F\t{n:x}\t{:x}", fold as u32); }
            if norm != c { println!("N\t{n:x}\t{:x}", norm as u32); }
            let class = if c.is_lowercase() { 3 } else if chars::is_upper_case(c) { 4 }
                else if c.is_numeric() { 6 } else if c.is_alphabetic() { 5 }
                else if c.is_whitespace() { 0 } else { 1 };
            if class != last_class { println!("C\t{n:x}\t{class:x}"); last_class = class; }
        }
        return;
    }
    let mut matcher = Matcher::new(Config::DEFAULT);
    let ranking = std::env::args().nth(1).as_deref() == Some("rank");
    for line in io::stdin().lock().lines() {
        let line = line.unwrap();
        let f: Vec<_> = line.split('\t').collect();
        assert!(f.len() >= 6);
        let case = match f[0] { "RESPECT" => CaseMatching::Respect, "IGNORE" => CaseMatching::Ignore, _ => CaseMatching::Smart };
        let norm = if f[1] == "NEVER" { Normalization::Never } else { Normalization::Smart };
        matcher.config = Config::DEFAULT;
        if f[2] == "PATHS" { matcher.config.set_match_paths(); }
        matcher.config.prefer_prefix = f[3] == "true";
        let query = decode(f[4]);
        if ranking {
            struct Entry(usize, String);
            impl AsRef<str> for Entry { fn as_ref(&self) -> &str { &self.1 } }
            let candidates = f[5..].iter().enumerate().map(|(i, s)| Entry(i, decode(s)));
            let atom = Atom::new(&query, case, norm, AtomKind::Fuzzy, false);
            let ranked = atom.match_list(candidates, &mut matcher);
            println!("{}", ranked.iter().map(|(e, score)| format!("{}:{}", e.0, score)).collect::<Vec<_>>().join(","));
            continue;
        }
        let haystack = decode(f[5]);
        let atom = Atom::new(&query, case, norm, AtomKind::Fuzzy, false);
        let mut buf = Vec::new();
        let h = Utf32Str::new(&haystack, &mut buf);
        let mut indices = Vec::new();
        let score = atom.indices(h, &mut matcher, &mut indices);
        let score_only = atom.score(h, &mut matcher);
        assert_eq!(score, score_only);
        match score {
            None => println!("-"),
            Some(s) => println!("{}\t{}", s, indices.iter().map(u32::to_string).collect::<Vec<_>>().join(",")),
        }
    }
}
