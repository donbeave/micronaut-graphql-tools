package io.micronaut.graphql.tools.apisnapshot

import java.io.DataInputStream
import java.io.File

private object Flags {
    const val ACC_PUBLIC = 0x0001
    const val ACC_PROTECTED = 0x0004
    const val ACC_ENUM = 0x4000
    const val INTERNAL_MARKER = "Lio/micronaut/core/annotation/Internal;"
}

/** One field or method of a class file, with the facts the API listing needs. */
internal class MemberInfo(val access: Int, val name: String, val descriptor: String, val internal: Boolean) {
    val exposed: Boolean get() = access and (Flags.ACC_PUBLIC or Flags.ACC_PROTECTED) != 0

    /** An enum constant: kept in declaration order, because its position is its ordinal. */
    val enumConstant: Boolean get() = access and Flags.ACC_ENUM != 0 && descriptor.startsWith("L")
}

/**
 * The few facts of a class file that javap does not give: the binary name, whether the class or one of its members carries
 * `@Internal`, and the access flags of a nested class (which only the InnerClasses attribute holds). Fields come first in
 * [members], then methods, in class-file order, which is the order javap prints them in. [permitted] is the list of a sealed
 * type's permitted subclasses, which javap does not print.
 */
internal class ClassFileInfo(
    val file: File,
    val name: String,
    val access: Int,
    val internal: Boolean,
    val innerAccess: Int?,
    val members: List<MemberInfo>,
    val permitted: List<String>,
) {
    val simpleFileName: String get() = name.substringAfterLast('/')
    val packageSegments: List<String> get() = name.split('/').dropLast(1)
    val nested: Boolean get() = innerAccess != null

    /** The binary name of the class that encloses this one, by the `$` convention, or null for a top-level class. */
    val outerName: String? get() = if (nested) name.substringBeforeLast('$') else null

    companion object {
        fun read(file: File): ClassFileInfo = DataInputStream(file.inputStream().buffered()).use { read(file, it) }

        private fun read(file: File, input: DataInputStream): ClassFileInfo {
            check(input.readInt() == 0xCAFEBABE.toInt()) { "$file is not a class file" }
            input.readUnsignedShort()
            input.readUnsignedShort()
            val utf8 = HashMap<Int, String>()
            val classNames = HashMap<Int, Int>()
            val count = input.readUnsignedShort()
            var index = 1
            while (index < count) {
                when (val tag = input.readUnsignedByte()) {
                    1 -> utf8[index] = input.readUTF()
                    7 -> classNames[index] = input.readUnsignedShort()
                    8, 16, 19, 20 -> input.skipNBytes(2)
                    15 -> input.skipNBytes(3)
                    3, 4, 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4)
                    5, 6 -> {
                        input.skipNBytes(8)
                        index++
                    }
                    else -> error("$file has an unknown constant pool tag $tag")
                }
                index++
            }
            val access = input.readUnsignedShort()
            val name = utf8.getValue(classNames.getValue(input.readUnsignedShort()))
            input.readUnsignedShort()
            input.skipNBytes(2L * input.readUnsignedShort())

            var innerAccess: Int? = null
            fun readAttributes(onAttribute: (String, Int) -> Boolean): Boolean {
                var internalHere = false
                repeat(input.readUnsignedShort()) {
                    val attribute = utf8.getValue(input.readUnsignedShort())
                    val length = input.readInt()
                    when (attribute) {
                        "RuntimeVisibleAnnotations", "RuntimeInvisibleAnnotations" ->
                            if (annotationsMention(input, utf8)) internalHere = true
                        else -> if (!onAttribute(attribute, length)) input.skipNBytes(length.toLong())
                    }
                }
                return internalHere
            }

            fun readMembers(): List<MemberInfo> = List(input.readUnsignedShort()) {
                val memberAccess = input.readUnsignedShort()
                val memberName = utf8.getValue(input.readUnsignedShort())
                val descriptor = utf8.getValue(input.readUnsignedShort())
                MemberInfo(memberAccess, memberName, descriptor, readAttributes { _, _ -> false })
            }

            val fields = readMembers()
            val methods = readMembers()
            var permitted = emptyList<String>()
            val internal = readAttributes { attribute, _ ->
                if (attribute == "PermittedSubclasses") {
                    permitted = List(input.readUnsignedShort()) { utf8.getValue(classNames.getValue(input.readUnsignedShort())) }
                    true
                } else if (attribute == "InnerClasses") {
                    repeat(input.readUnsignedShort()) {
                        val inner = input.readUnsignedShort()
                        input.readUnsignedShort()
                        input.readUnsignedShort()
                        val flags = input.readUnsignedShort()
                        if (utf8[classNames[inner]] == name) innerAccess = flags
                    }
                    true
                } else {
                    false
                }
            }
            return ClassFileInfo(file, name, access, internal, innerAccess, fields + methods, permitted)
        }

        /** Reads an annotations attribute whole and tells whether one of its annotations is `@Internal`. */
        private fun annotationsMention(input: DataInputStream, utf8: Map<Int, String>): Boolean {
            var found = false
            repeat(input.readUnsignedShort()) {
                if (readAnnotation(input, utf8) == Flags.INTERNAL_MARKER) found = true
            }
            return found
        }

        private fun readAnnotation(input: DataInputStream, utf8: Map<Int, String>): String? {
            val type = utf8[input.readUnsignedShort()]
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort()
                skipElementValue(input, utf8)
            }
            return type
        }

        private fun skipElementValue(input: DataInputStream, utf8: Map<Int, String>) {
            when (val tag = input.readUnsignedByte().toChar()) {
                'e' -> input.skipNBytes(4)
                '@' -> readAnnotation(input, utf8)
                '[' -> repeat(input.readUnsignedShort()) { skipElementValue(input, utf8) }
                'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z', 's', 'c' -> input.skipNBytes(2)
                else -> error("unknown annotation element tag $tag")
            }
        }
    }
}
