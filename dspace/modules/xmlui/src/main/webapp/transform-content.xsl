<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns="http://di.tamu.edu/DRI/1.0/"
                xmlns:dri="http://di.tamu.edu/DRI/1.0/"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:i18n="http://apache.org/cocoon/i18n/2.1"
                exclude-result-prefixes="xsl dri i18n">

  <xsl:output indent="yes"/>        

  <!-- Identity template, copies everything as is -->
   <xsl:template match="@*|node()">
      <xsl:copy>
         <xsl:apply-templates select="@*|node()" />
      </xsl:copy>
   </xsl:template>    

<!-- Except -->
   <xsl:template match="dri:leaf" />
    <xsl:template match="dri:localizedName" />
    <xsl:template match="dri:name" />
    <xsl:template match="dri:path" />
    <xsl:template match="dri:primaryNodeTypeName" />
    <xsl:template match="dri:canonicalHandleUuid" />
    <xsl:template match="dri:title" />
</xsl:stylesheet>