package com.devstronomy;

import com.devstronomy.jooq.generated.tables.records.PlanetRecord;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static com.devstronomy.jooq.generated.Tables.PLANET;
import static com.devstronomy.jooq.generated.Tables.SATELLITE;

/**
 * Reads data from planets CSV file and insert them into the SQL database.
 */
final class Converter {

    // TODO: do not paths.
    private static final String PLANETS_CSV_PATH = "../data/planets.csv";
    private static final String SATELLITES_CSV_PATH = "../data/satellites.csv";

    private final DSLContext jooqDslContext;

    public static void doConversion(Connection conn) {
        new Converter(conn).convert();
    }

    private Converter(Connection conn) {
        jooqDslContext = DSL.using(conn, SQLDialect.MYSQL);
    }

    private void convert() {
        try {
            Map<String, Integer> planetNameToId = new HashMap<>();
            for (String[] planetLine : prepareCsvReader(PLANETS_CSV_PATH)) {
                Integer planetId = insertPlanetIntoDb(planetLine);
                planetNameToId.put(planetLine[0], planetId);
            }
            for (String[] satelliteLine : prepareCsvReader(SATELLITES_CSV_PATH)) {
                insertSatelliteIntoDb(satelliteLine, planetNameToId);
            }
        } catch (SQLException e) {
            System.err.println("Cannot obtain database connection:");
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private CSVReader prepareCsvReader(String csvFilePath) throws IOException {
        Reader reader = Files.newBufferedReader(Paths.get(csvFilePath));
        return new CSVReaderBuilder(reader)
                .withCSVParser(new CSVParserBuilder().build())
                .withSkipLines(1) // skip header
                .build();
    }

    private Integer insertPlanetIntoDb(String[] planetLine) throws SQLException {
        // TODO: Try to find a less verbose, yet typesafe, way to insert data with JOOQ.
        return jooqDslContext
                .insertInto(PLANET,
                        PLANET.NAME, PLANET.MASS, PLANET.DIAMETER, PLANET.DENSITY, PLANET.GRAVITY,
                        PLANET.ESCAPE_VELOCITY, PLANET.ROTATION_PERIOD, PLANET.LENGTH_OF_DAY,
                        PLANET.DISTANCE_FROM_SUN, PLANET.PERIHELION, PLANET.APHELION, PLANET.ORBITAL_PERIOD,
                        PLANET.ORBITAL_VELOCITY, PLANET.ORBITAL_INCLINATION, PLANET.ORBITAL_ECCENTRICITY,
                        PLANET.OBLIQUITY_TO_ORBIT, PLANET.MEAN_TEMPERATURE, PLANET.SURFACE_PRESSURE,
                        PLANET.NUMBER_OF_MOONS, PLANET.HAS_RING_SYSTEM, PLANET.HAS_GLOBAL_MAGNETIC_FIELD)
                .values(planetLine[0], toBD(planetLine[1]), toBD(planetLine[2]), toBD(planetLine[3]),
                        toBD(planetLine[4]), toBD(planetLine[5]), toBD(planetLine[6]), toBD(planetLine[7]),
                        toBD(planetLine[8]), toBD(planetLine[9]), toBD(planetLine[10]), toBD(planetLine[11]),
                        toBD(planetLine[12]), toBD(planetLine[13]), toBD(planetLine[14]), toBD(planetLine[15]),
                        toBD(planetLine[16]), toBD(planetLine[17]), Integer.valueOf(planetLine[18]),
                        Boolean.valueOf(planetLine[19]), Boolean.valueOf(planetLine[20]))
                .returning(PLANET.ID)
                .fetchOne()
                .get(PLANET.ID);
    }

    private void insertSatelliteIntoDb(String[] satellite, Map<String, Integer> planetNameToId) throws SQLException {
        // TODO: Try to find a less verbose, yet typesafe, way to insert data with JOOQ.
        String parentPlanet = satellite[0];
        Integer planetId = planetNameToId.get(parentPlanet);
        jooqDslContext
                .insertInto(SATELLITE,
                        SATELLITE.PLANET_ID, SATELLITE.NAME, SATELLITE.GM, SATELLITE.RADIUS,
                        SATELLITE.DENSITY, SATELLITE.MAGNITUDE, SATELLITE.ALBEDO)
                .values(planetId, satellite[1], toBD(satellite[2]), toBD(satellite[3]),
                        toBD(satellite[4]), toBD(satellite[5]), toBD(satellite[6]))
                .execute();
    }

    private static BigDecimal toBD(String str) {
        // 'Unknown*' is used as a value for a surface pressure of giant gas planets.
        // See https://nssdc.gsfc.nasa.gov/planetary/factsheet/planetfact_notes.html#surp for more.
        //
        // '?' is used for unknown parameters in https://ssd.jpl.nasa.gov/?sat_phys_par
        if (str.equals("Unknown*") || str.equals("?")) {
            return null;
        }

        // TODO: Handle errors behind the '±'. E.g. provide a separate column in the database.
        int plusMinusIdx = str.indexOf('±');
        if (plusMinusIdx != -1) {
            BigDecimal result = toBD(str.substring(0, plusMinusIdx));
            System.out.printf("INFO: Coercing '%s' to '%s'\n", str, result);
            return result;
        }

        // TODO: Handle differences. Do not lose the information!
        if (str.endsWith("R") || str.endsWith("r") || str.endsWith("V")) {
            BigDecimal result = toBD(str.substring(0, str.length() - 1));
            System.out.printf("WARN: Coercing '%s' to '%s' (to be fixed)\n", str, result);
            return result;
        }

        try {
            return new BigDecimal(str);
        } catch (NumberFormatException nfe) {
            throw new RuntimeException("Cannot convert string to a number: " + str, nfe);
        }
    }

}