package com.scsabot.discordbot.roles;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Country/territory flag mapping generated from the JDK's ISO 3166 data (names, flags) plus a
 * compact region grouping used to organise the role-selection cards.
 * Flag emoji = two regional-indicator code points.
 */
public final class CountryFlags {

    public record Country(String code, String name, String flag, String region) {
        public String roleName() {
            return flag + " " + name;
        }
    }

    private static final String OTHER = "Other";

    private static final List<Country> ALL;
    private static final Map<String, List<Country>> BY_REGION;
    private static final Map<String, Country> BY_FLAG = new HashMap<>();
    private static final Map<String, Country> BY_ROLE_NAME = new HashMap<>();

    static {
        // Region -> ISO codes. Anything not listed (e.g. Antarctica) falls back to "Other".
        Map<String, List<String>> regionDefs = new LinkedHashMap<>();
        regionDefs.put("Asia", codes("AF AM AZ BH BD BT BN KH CN CY GE HK IN ID IR IQ IL JP JO KZ KW KG LA LB MO MY "
                + "MV MN MM NP KP OM PK PS PH QA SA SG KR LK SY TW TJ TH TL TR TM AE UZ VN YE IO"));
        regionDefs.put("Europe", codes("AL AD AT BY BE BA BG HR CZ DK EE FO FI FR DE GI GR GG VA HU IS IE IM IT JE XK "
                + "LV LI LT LU MT MD MC ME NL MK NO PL PT RO RU SM RS SK SI ES SJ SE CH UA GB AX"));
        regionDefs.put("Africa", codes("DZ AO BJ BW BF BI CV CM CF TD KM CG CD CI DJ EG GQ ER SZ ET GA GM GH GN GW KE "
                + "LS LR LY MG MW ML MR MU YT MA MZ NA NE NG RE RW SH ST SN SC SL SO ZA SS SD TZ TG TN UG EH ZM ZW"));
        regionDefs.put("Americas", codes("AI AG AR AW BS BB BZ BM BO BQ BR CA KY CL CO CR CU CW DM DO EC SV FK GF GL "
                + "GD GP GT GY HT HN JM MQ MX MS NI PA PY PE PR BL KN LC MF PM VC SX SR TT TC US UY VE VG VI GS"));
        regionDefs.put("Oceania", codes("AS AU CK FJ PF GU KI MH FM NR NC NZ NU NF MP PW PG PN WS SB TK TO TV VU WF "
                + "UM CX CC HM"));

        Map<String, String> regionOf = new HashMap<>();
        regionDefs.forEach((region, codes) -> codes.forEach(code -> regionOf.put(code, region)));

        Map<String, String> names = new TreeMap<>();
        for (String code : Locale.getISOCountries()) {
            String name = Locale.of("", code).getDisplayCountry(Locale.ENGLISH);
            if (!name.isBlank() && !name.equalsIgnoreCase(code)) {
                names.put(code, name);
            }
        }
        names.putIfAbsent("XK", "Kosovo"); // not in ISO 3166-1 but has a flag emoji

        List<Country> list = new ArrayList<>();
        names.forEach((code, name) -> {
            Country country = new Country(code, name, toFlag(code), regionOf.getOrDefault(code, OTHER));
            list.add(country);
            BY_FLAG.put(country.flag(), country);
            BY_ROLE_NAME.put(country.roleName(), country);
        });

        Collator collator = Collator.getInstance(Locale.ENGLISH); // sorts "Åland Islands" under A
        list.sort((a, b) -> collator.compare(a.name(), b.name()));
        ALL = Collections.unmodifiableList(list);

        Map<String, List<Country>> byRegion = new LinkedHashMap<>();
        regionDefs.keySet().forEach(region -> byRegion.put(region, new ArrayList<>()));
        byRegion.put(OTHER, new ArrayList<>());
        for (Country country : list) {
            byRegion.get(country.region()).add(country);
        }
        byRegion.values().removeIf(List::isEmpty);
        BY_REGION = Collections.unmodifiableMap(byRegion);
    }

    private CountryFlags() {
    }

    public static List<Country> all() {
        return ALL;
    }

    /**
     * Region name -> countries (alphabetical), in display order: Asia, Europe, Africa, Americas, Oceania, Other.
     */
    public static Map<String, List<Country>> byRegion() {
        return BY_REGION;
    }

    public static Optional<Country> fromFlag(String emoji) {
        if (emoji == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_FLAG.get(emoji.replace("️", "")));
    }

    public static Optional<Country> fromRoleName(String roleName) {
        return Optional.ofNullable(BY_ROLE_NAME.get(roleName));
    }

    private static List<String> codes(String spaceSeparated) {
        return Arrays.asList(spaceSeparated.trim().split("\\s+"));
    }

    private static String toFlag(String code) {
        StringBuilder sb = new StringBuilder();
        for (char c : code.toUpperCase(Locale.ROOT).toCharArray()) {
            sb.appendCodePoint(0x1F1E6 + (c - 'A'));
        }
        return sb.toString();
    }
}