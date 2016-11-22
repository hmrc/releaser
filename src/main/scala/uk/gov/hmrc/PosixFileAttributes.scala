/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc

import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission._

import scala.collection.JavaConversions.setAsJavaSet
import scala.collection.mutable.ListBuffer

object PosixFileAttributes {

  implicit def convertToPermissionsSet(mode: Int): java.util.Set[PosixFilePermission] = {

    def isSet(mode: Int, testbit: Int) = {
      (mode & testbit) == testbit
    }

    val result = ListBuffer[PosixFilePermission]()

    if (isSet(mode, 256)) { // 0400
      result += OWNER_READ
    }
    if (isSet(mode, 128)) { // 0200
      result += OWNER_WRITE
    }
    if (isSet(mode, 64)) { // 0100
      result += OWNER_EXECUTE
    }
    if (isSet(mode, 32)) { // 040
      result += GROUP_READ
    }
    if (isSet(mode, 16)) { // 020
      result += GROUP_WRITE
    }
    if (isSet(mode, 8)) { // 010
      result += GROUP_EXECUTE
    }
    if (isSet(mode, 4)) { // 04
      result += OTHERS_READ
    }
    if (isSet(mode, 2)) { // 02
      result += OTHERS_WRITE
    }
    if (isSet(mode, 1)) { // 01
      result += OTHERS_EXECUTE
    }
    setAsJavaSet(result.toSet)
  }

  implicit def convertToInt(permissions: java.util.Set[PosixFilePermission]): Int = {

    var result = 0
    if (permissions.contains(OWNER_READ)) {
      result = result | 256 //0400
    }
    if (permissions.contains(OWNER_WRITE)) {
      result = result | 128 // 0200
    }
    if (permissions.contains(OWNER_EXECUTE)) {
      result = result | 64 // 0100
    }
    if (permissions.contains(GROUP_READ)) {
      result = result | 32 // 040
    }
    if (permissions.contains(GROUP_WRITE)) {
      result = result | 16 // 020
    }
    if (permissions.contains(GROUP_EXECUTE)) {
      result = result | 8 // 010
    }
    if (permissions.contains(OTHERS_READ)) {
      result = result | 4 // 04
    }
    if (permissions.contains(OTHERS_WRITE)) {
      result = result | 2 // 02
    }
    if (permissions.contains(OTHERS_EXECUTE)) {
      result = result | 1 // 01
    }
    result
  }
}
