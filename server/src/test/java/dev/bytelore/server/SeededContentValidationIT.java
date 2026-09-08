package dev.bytelore.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * The content sweep of {@link ContentValidationSweep}, applied to the database the rest of the
 * suite shares: the sample seed the migrations insert as SQL, plus the small fixture corpus the
 * test profile points the loader at.
 *
 * <p>This is the half of the sweep that covers rows written by hand. The sample seed went nowhere
 * near the write boundary -- it is {@code INSERT} statements -- so nothing had ever examined it
 * until this ran, and the same will be true of whatever a future migration inserts the same way.
 *
 * <p>The corpus that ships is swept by {@link AuthoredCorpusLoadIT} instead, against a database of
 * its own, because the two corpora cannot be loaded into the same one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SeededContentValidationIT extends ContentValidationSweep {}
