/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2019 Yegor Bugayenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.jpeek;

import com.jcabi.log.Logger;
import com.jcabi.xml.ClasspathSources;
import com.jcabi.xml.StrictXML;
import com.jcabi.xml.XML;
import com.jcabi.xml.XMLDocument;
import com.jcabi.xml.XSD;
import com.jcabi.xml.XSDDocument;
import com.jcabi.xml.XSL;
import com.jcabi.xml.XSLChain;
import com.jcabi.xml.XSLDocument;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.cactoos.collection.CollectionOf;
import org.cactoos.io.TeeInput;
import org.cactoos.scalar.LengthOf;
import org.xembly.Directives;
import org.xembly.Xembler;

/**
 * Single report.
 *
 * <p>There is no thread-safety guarantee.
 *
 * @since 0.1
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 */
final class XslReport implements Report {
    /**
     * Default mean.
     */
    private static final double DEFAULT_MEAN = 0.5d;

    /**
     * Default sigma.
     */
    private static final double DEFAULT_SIGMA = 0.1d;

    /**
     * Location to the schema file.
     */
    private static final String SCHEMA_FILE = "xsd/metric.xsd";

    /**
     * XSD schema.
     */
    private static final XSD SCHEMA = XSDDocument.make(
        XslReport.class.getResourceAsStream(XslReport.SCHEMA_FILE)
    );

    /**
     * XSL stylesheet.
     */
    private static final XSL STYLESHEET = XSLDocument.make(
        XslReport.class.getResourceAsStream("xsl/metric.xsl")
    ).with(new ClasspathSources());

    /**
     * The skeleton.
     */
    private final XML skeleton;

    /**
     * The metric.
     */
    private final String metric;

    /**
     * Calculus.
     */
    private final Calculus calculus;

    /**
     * Post processing XSLs.
     */
    private final XSL post;

    /**
     * XSL params.
     */
    private final Map<String, Object> params;

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of the metric
     * @param calc Calculus
     */
    XslReport(final XML xml, final String name, final Calculus calc) {
        this(
            xml, name, new HashMap<>(0), calc,
            XslReport.DEFAULT_MEAN, XslReport.DEFAULT_SIGMA
        );
    }

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of metric
     * @param args Params for XSL
     * @param calc Calculus
     * @checkstyle ParameterNumberCheck (5 lines)
     */
    XslReport(final XML xml, final String name, final Map<String, Object> args,
        final Calculus calc) {
        this(
            xml, name, args, calc,
            XslReport.DEFAULT_MEAN, XslReport.DEFAULT_SIGMA
        );
    }

    /**
     * Ctor.
     * @param xml Skeleton
     * @param name Name of the metric
     * @param args Params for XSL
     * @param calc Calculus
     * @param mean Mean
     * @param sigma Sigma
     * @todo #390:30min this constructor now has too many arguments. We should find a way
     *  to refactor the constructor or the class to have fewer parameters.
     *  We could start by analyzing the usage of this.params (args in this
     *  constructor) and get rid of it if it is not used.
     *  Another idea could be to have a data class contaning reporting params:
     *  name, args, mean, sigma.
     * @checkstyle ParameterNumberCheck (10 lines)
     */
    XslReport(final XML xml, final String name,
        final Map<String, Object> args, final Calculus calc,
        final double mean, final double sigma) {
        this.skeleton = xml;
        this.metric = name;
        this.params = args;
        this.calculus = calc;
        this.post = new XSLChain(
            new CollectionOf<>(
                new XSLDocument(
                    XslReport.class.getResourceAsStream(
                        "xsl/metric-post-colors.xsl"
                    )
                ).with("low", mean - sigma).with("high", mean + sigma),
                new XSLDocument(
                    XslReport.class.getResourceAsStream(
                        "xsl/metric-post-range.xsl"
                    )
                ),
                new XSLDocument(
                    XslReport.class.getResourceAsStream(
                        "xsl/metric-post-bars.xsl"
                    )
                )
            )
        );
    }

    /**
     * Save report.
     * @param target Target dir
     * @throws IOException If fails
     */
    @SuppressWarnings("PMD.GuardLogStatement")
    public void save(final Path target) throws IOException {
        final long start = System.currentTimeMillis();
        final XML xml = new StrictXML(
            new ReportWithStatistics(
                this.post.transform(this.xml())
            ),
            XslReport.SCHEMA
        );
        new LengthOf(
            new TeeInput(
                xml.toString(),
                target.resolve(
                    String.format("%s.xml", this.metric)
                )
            )
        ).intValue();
        new LengthOf(
            new TeeInput(
                XslReport.STYLESHEET.transform(xml).toString(),
                target.resolve(
                    String.format("%s.html", this.metric)
                )
            )
        ).intValue();
        Logger.debug(
            this, "%s.xml generated in %[ms]s",
            this.metric, System.currentTimeMillis() - start
        );
    }

    /**
     * Make XML.
     * @return XML
     * @throws IOException If fails
     * @todo #227:30min Add a test to check whether passing params to
     *  XSLDocument really works. Currently only C3 metric template
     *  is known to use parameter named 'ctors'. However C3.xsl is a
     *  work in progress and has impediments, see #175. In case the
     *  parameter becomes obsolete, consider simplifying construction
     *  of XSLDocument without params (see reviews to #326).
     */
    private XML xml() throws IOException {
        return new XMLDocument(
            new Xembler(
                new Directives()
                    .xpath("/metric")
                    .attr(
                        "xmlns:xsi",
                        "http://www.w3.org/2001/XMLSchema-instance"
                    )
                    .attr(
                        "xsi:noNamespaceSchemaLocation",
                        XslReport.SCHEMA_FILE
                    )
            ).applyQuietly(
                this.calculus.node(
                    this.metric, this.params, this.skeleton
                ).node()
            )
        );
    }

}
