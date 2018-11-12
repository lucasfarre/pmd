<?xml version="1.0" encoding="UTF-8"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xpath-default-namespace="http://pmd.sourceforge.net/report/2.0.0" version="2.0">

<xsl:variable name="cvsweb">http://doc.ece.uci.edu/cvs/viewcvs.cgi/Zen/packages/src/</xsl:variable>

<xsl:template match="pmd">
<html>
<head>
    <title>PMD Report</title>
    <style type="text/css">
        body { margin-left: 2%; margin-right: 2%; font:normal verdana,arial,helvetica; color:#000000; }
        table.details tr th { font-weight: bold; text-align:left; background:#a6caf0; }
        table.details tr td { background:#eeeee0; }
        table.summary tr th { font-weight: bold; text-align:left; background:#a6caf0; }
        table.summary tr td { background:#eeeee0; text-align:center;}
        .p1 { background:#FF9999; }
        .p2 { background:#FFCC66; }
        .p3 { background:#FFFF99; }
        .p4 { background:#99FF99; }
        .p5 { background:#9999FF; }

    </style>
</head>
<body>
    <H1>PMD Report</H1>
    <hr/>
    <h2>Summary</h2>
    <table border="0" class="summary">
      <tr>
        <th>Files</th>
        <th>Total</th>
        <th>Priority 1</th>
        <th>Priority 2</th>
        <th>Priority 3</th>
        <th>Priority 4</th>
        <th>Priority 5</th>
      </tr>
      <tr>
        <td><xsl:value-of select="count(//file)"/></td>
        <td><xsl:value-of select="count(//violation)"/></td>
        <td><div class="p1"><xsl:value-of select="count(//violation[@priority = 1])"/></div></td>
        <td><div class="p2"><xsl:value-of select="count(//violation[@priority = 2])"/></div></td>
        <td><div class="p3"><xsl:value-of select="count(//violation[@priority = 3])"/></div></td>
        <td><div class="p4"><xsl:value-of select="count(//violation[@priority = 4])"/></div></td>
        <td><div class="p5"><xsl:value-of select="count(//violation[@priority = 5])"/></div></td>
      </tr>
    </table>
    <hr/>
    <xsl:for-each select="file">
        <xsl:sort data-type="number" order="descending" select="count(violation)"/>
        <xsl:variable name="filename" select="@name"/>
        <H3><xsl:value-of disable-output-escaping="yes" select="substring-before(translate(@name,'/','.'),'.java')"/></H3>
        <table border="0" width="100%" class="details">
            <tr>
                <th>Begin Line</th>
                <th align="left">Description</th>
            </tr>

	    <xsl:for-each select="violation">
		    <tr>
			<td style="padding: 3px" align="right"><a><xsl:attribute name="href"><xsl:value-of select="$cvsweb"/><xsl:value-of select="$filename"/>?annotate=HEAD#<xsl:value-of disable-output-escaping="yes" select="@beginline"/></xsl:attribute><xsl:value-of disable-output-escaping="yes" select="@beginline"/></a></td>
			<td style="padding: 3px" align="left" width="100%"><xsl:value-of disable-output-escaping="yes" select="."/></td>
		    </tr>
	    </xsl:for-each>

        </table>
        <br/>
    </xsl:for-each>
    <p>Generated by <a href="http://pmd.sourceforge.net">PMD <b><xsl:value-of select="//pmd/@version"/></b></a> on <xsl:value-of select="//pmd/@timestamp"/>.</p>
</body>
</html>
</xsl:template>

</xsl:stylesheet>
