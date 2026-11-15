package de.jpx3.intave.version;

import de.jpx3.intave.resource.Resources;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtocolVersionTableTest {
  private static final Pattern RELEASE = Pattern.compile("release (\\d+) is (\\S+)");

  /** The client version picks block translations and outdated-client handling, so ranges must match releases. */
  @Test
  void everyReleaseProtocolResolvesToOneOfItsVersions() {
    ProtocolVersionRanges ranges = Resources.resourceFromJarOrBuild("protocolversions")
      .collectLines(ProtocolVersionRangesCompiler.resourceCollector());
    int checked = 0;
    for (String line : Resources.resourceFromJarOrBuild("protocolversions").readLines()) {
      Matcher matcher = RELEASE.matcher(line.trim());
      if (!matcher.matches()) {
        continue;
      }
      int protocol = Integer.parseInt(matcher.group(1));
      List<String> versions = Arrays.asList(matcher.group(2).split(","));
      String resolved = ranges.byProtocolVersion(protocol);
      assertTrue(versions.contains(resolved), "protocol " + protocol + " resolves to " + resolved + ", release lists " + versions);
      checked++;
    }
    assertTrue(checked > 40, "release lines parsed: " + checked);
  }

  /** A downloaded table replaces the bundled ranges, so a gap there must not rename a release. */
  @Test
  void releaseDeclarationsWinOverRangeGaps() {
    ProtocolVersionRanges ranges = java.util.stream.Stream.of(
      "up to 768 is 1.21.3", "up to 774 is 1.21.11",
      "release 768 is 1.21.2,1.21.3", "release 769 is 1.21.4", "release 772 is 1.21.7,1.21.8", "release 774 is 1.21.11"
    ).collect(ProtocolVersionRangesCompiler.resourceCollector());
    org.junit.jupiter.api.Assertions.assertEquals("1.21.4", ranges.byProtocolVersion(769));
    org.junit.jupiter.api.Assertions.assertEquals("1.21.8", ranges.byProtocolVersion(772));
    org.junit.jupiter.api.Assertions.assertEquals("1.21.3", ranges.byProtocolVersion(768));
    org.junit.jupiter.api.Assertions.assertEquals("1.21.11", ranges.byProtocolVersion(774));
    org.junit.jupiter.api.Assertions.assertEquals("1.21.11", ranges.byProtocolVersion(770));
  }
}
