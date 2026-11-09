package com.majlisengineer.data
object MaterialSearchNormalizer {
 fun normalize(value:String):String = value.lowercase()
  .replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ى','ي').replace('ة','ه')
  .replace(Regex("[\\u064B-\\u065F\\u0670]"),"")
  .replace(Regex("[^\\p{L}\\p{N}\\s]")," ")
  .replace(Regex("\\s+")," ").trim()
}
