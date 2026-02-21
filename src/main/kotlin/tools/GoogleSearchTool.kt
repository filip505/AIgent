package tools

import com.google.genai.types.GoogleSearch
import com.google.genai.types.Tool

val googleSearchTool: Tool = Tool.builder()
    .googleSearch(GoogleSearch.builder().build())
    .build()
