package test.os

import os.zip
import test.os.TestUtil.prep
import utest._

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, PrintStream}
import java.nio.file.attribute.PosixFilePermission
import java.util.zip.{ZipEntry, ZipOutputStream}

object ZipOpTests extends TestSuite {

  def tests = Tests {
    // This test seems really flaky for some reason
    // test("level") - prep { wd =>
    //   val zipsForLevel = for (i <- Range.inclusive(0, 9)) yield {
    //     os.write.over(wd / "File.txt", Range(0, 1000).map(x => x.toString * x))
    //     os.zip(
    //       dest = wd / s"archive-$i.zip",
    //       sources = Seq(
    //         wd / "File.txt",
    //         wd / "folder1"
    //       ),
    //       compressionLevel = i
    //     )
    //   }

    //   // We can't compare every level because compression isn't fully monotonic,
    //   // but we compare some arbitrary levels just to sanity check things

    //   // Uncompressed zip is definitely bigger than first level of compression
    //   assert(os.size(zipsForLevel(0)) > os.size(zipsForLevel(1)))
    //   // First level of compression is bigger than middle compression
    //   assert(os.size(zipsForLevel(1)) > os.size(zipsForLevel(5)))
    //   // Middle compression is bigger than best compression
    //   assert(os.size(zipsForLevel(5)) > os.size(zipsForLevel(9)))
    // }
    test("renaming") - prep { wd =>
      val zipFileName = "zip-file-test.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          // renaming files and folders
          wd / "File.txt" -> os.sub / "renamed-file.txt",
          wd / "folder1" -> os.sub / "renamed-folder"
        )
      )

      val unzippedFolder = os.unzip(
        source = zipFile1,
        dest = wd / "unzipped folder"
      )

      val paths = os.walk(unzippedFolder)
      val expected = Seq(
        wd / "unzipped folder/renamed-file.txt",
        wd / "unzipped folder/renamed-folder",
        wd / "unzipped folder/renamed-folder/one.txt"
      )
      assert(paths.sorted == expected)
    }

    test("excludePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByExcludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        excludePatterns = Seq(".*\\.txt".r)
      )

      // Unzip file to check for contents
      val outputZipFilePath = os.unzip(
        zipFile1,
        dest = wd / "zipByExcludingCertainFiles"
      )
      val paths = os.walk(outputZipFilePath).sorted
      val expected = Seq(wd / "zipByExcludingCertainFiles/File.amx")
      assert(paths == expected)
    }

    test("includePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      // Zipping files and folders in a new zip file
      val zipFileName = "zipByIncludingCertainFiles.zip"
      val zipFile1: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "Multi Line.txt"
        ),
        includePatterns = Seq(".*\\.amx".r)
      )

      // Unzip file to check for contents
      val outputZipFilePath =
        os.unzip(zipFile1, dest = wd / "zipByIncludingCertainFiles")
      val paths = os.walk(outputZipFilePath)
      val expected = Seq(wd / "zipByIncludingCertainFiles" / amxFile)
      assert(paths == expected)
    }

    test("zipEmptyDir") {
      def prepare(wd: os.Path) = {
        val zipFileName = "zipEmptyDirs"

        val emptyDir = wd / "empty"
        os.makeDir(emptyDir)

        val containsEmptyDir = wd / "outer"
        os.makeDir.all(containsEmptyDir)
        os.makeDir(containsEmptyDir / "emptyInnerDir")

        (zipFileName, emptyDir, containsEmptyDir)
      }

      test("zipEmptyDir") - prep { wd =>
        val (zipFileName, emptyDir, containsEmptyDir) = prepare(wd)

        val zipped = os.zip(
          dest = wd / s"${zipFileName}.zip",
          sources = Seq(emptyDir, containsEmptyDir)
        )

        val unzipped = os.unzip(zipped, wd / zipFileName)
        // should include empty dirs inside source
        assert(os.isDir(unzipped / "emptyInnerDir"))
        // should ignore empty dirs specified in sources without dest
        assert(!os.exists(unzipped / "empty"))
      }

      test("includePatterns") - prep { wd =>
        val (zipFileName, _, containsEmptyDir) = prepare(wd)

        val zipped = os.zip(
          dest = wd / s"${zipFileName}.zip",
          sources = Seq(containsEmptyDir),
          includePatterns = Seq(raw".*Inner.*".r)
        )

        val unzipped = os.unzip(zipped, wd / zipFileName)
        assert(os.isDir(unzipped / "emptyInnerDir"))
      }

      test("excludePatterns") - prep { wd =>
        val (zipFileName, _, containsEmptyDir) = prepare(wd)

        val zipped = os.zip(
          dest = wd / s"${zipFileName}.zip",
          sources = Seq(containsEmptyDir),
          excludePatterns = Seq(raw".*Inner.*".r)
        )

        val unzipped = os.unzip(zipped, wd / zipFileName)
        assert(!os.exists(unzipped / "emptyInnerDir"))
      }

      test("withDest") - prep { wd =>
        val (zipFileName, emptyDir, _) = prepare(wd)

        val zipped = os.zip(
          dest = wd / s"${zipFileName}.zip",
          sources = Seq((emptyDir, os.sub / "empty"))
        )

        val unzipped = os.unzip(zipped, wd / zipFileName)
        // should include empty dirs specified in sources with dest
        assert(os.isDir(unzipped / "empty"))
      }
    }

    test("zipStream") - prep { wd =>
      val zipFileName = "zipStreamFunction.zip"

      val stream = os.write.outputStream(wd / "zipStreamFunction.zip")

      val writable = zip.stream(sources = Seq(wd / "File.txt"))

      writable.writeBytesTo(stream)
      stream.close()

      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        dest = wd / "zipStreamFunction"
      )

      val paths = os.walk(unzippedFolder)
      assert(paths == Seq(unzippedFolder / "File.txt"))
    }

    test("list") - prep { wd =>
      // Zipping files and folders in a new zip file
      val zipFileName = "listContentsOfZipFileWithoutExtracting.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / "folder1"
        )
      )

      // Unzip file to a destination folder
      val listedContents = os.unzip.list(source = wd / zipFileName).toSeq

      val expected = Seq(os.sub / "File.txt", os.sub / "one.txt")
      assert(listedContents == expected)
    }

    test("unzipExcludePatterns") - prep { wd =>
      val amxFile = "File.amx"
      os.copy(wd / "File.txt", wd / amxFile)

      val zipFileName = "unzipAllExceptExcludingCertainFiles.zip"
      val zipFile: os.Path = os.zip(
        dest = wd / zipFileName,
        sources = Seq(
          wd / "File.txt",
          wd / amxFile,
          wd / "folder1"
        )
      )

      // Unzip file to a destination folder
      val unzippedFolder = os.unzip(
        source = wd / zipFileName,
        dest = wd / "unzipAllExceptExcludingCertainFiles",
        excludePatterns = Seq(amxFile.r)
      )

      val paths = os.walk(unzippedFolder)
      val expected = Seq(
        wd / "unzipAllExceptExcludingCertainFiles/File.txt",
        wd / "unzipAllExceptExcludingCertainFiles/one.txt"
      )

      assert(paths.toSet == expected.toSet)
    }

    test("zipList") - prep { wd =>
      val sources = wd / "folder1"
      val zipFilePath = os.zip(
        dest = wd / "my.zip",
        sources = os.list(sources)
      )

      val expected = os.unzip.list(source = zipFilePath).map(_.resolveFrom(sources)).toSet
      assert(os.list(sources).toSet == expected)
    }

    test("symLinkAndPermissions") {
      def prepare(
          wd: os.Path,
          zipStream: Boolean = false,
          unzipStream: Boolean = false,
          followLinks: Boolean = true
      ) = {
        val zipFileName = "zipped.zip"
        val source = wd / "folder2"
        val link = os.rel / "nestedA" / "link.txt"
        if (!scala.util.Properties.isWin) {
          os.perms.set(source / "nestedA", os.PermSet.fromString("rwxrwxrwx"))
          os.perms.set(source / "nestedA" / "a.txt", os.PermSet.fromString("rw-rw-rw-"))
          os.symlink(source / link, os.rel / "a.txt")
        }

        val zipped =
          if (zipStream) {
            os.write(
              wd / zipFileName,
              os.zip.stream(sources = List(source), followLinks = followLinks)
            )
            wd / zipFileName
          } else {
            os.zip(
              dest = wd / zipFileName,
              sources = List(source),
              followLinks = followLinks
            )
          }

        val unzipped =
          if (unzipStream) {
            os.unzip.stream(
              source = os.read.inputStream(zipped),
              dest = wd / "unzipped"
            )
            wd / "unzipped"
          } else {
            os.unzip(
              dest = wd / "unzipped",
              source = zipped
            )
          }

        (source, unzipped, link)
      }

      def walkRel(p: os.Path) = os.walk(p).map(_.relativeTo(p))

      test("zip") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, followLinks = true)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)
          // test all permissions are preserved
          assert(os.walk.stream(source)
            .filter(!os.isLink(_))
            .forall(p => os.perms(p) == os.perms(unzipped / p.relativeTo(source))))

          // test symlinks are zipped as the referenced files
          val unzippedLink = unzipped / link
          assert(os.isFile(unzippedLink))
          assert(os.read(os.readLink.absolute(source / link)) == os.read(unzippedLink))
        }
      }

      test("zipPreserveLinks") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, followLinks = false)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)
          // test all permissions are preserved
          assert(os.walk.stream(source)
            .filter(!os.isLink(_))
            .forall(p => os.perms(p) == os.perms(unzipped / p.relativeTo(source))))

          // test symlinks are zipped as symlinks
          val unzippedLink = unzipped / link
          assert(os.isLink(unzippedLink))
          assert(os.readLink(source / link) == os.readLink(unzippedLink))
        }
      }

      test("zipStream") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, zipStream = true, followLinks = true)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)
          // test all permissions are preserved
          assert(os.walk.stream(source)
            .filter(!os.isLink(_))
            .forall(p => os.perms(p) == os.perms(unzipped / p.relativeTo(source))))

          // test symlinks are zipped as the referenced files
          val unzippedLink = unzipped / link
          assert(os.isFile(unzippedLink))
          assert(os.read(os.readLink.absolute(source / link)) == os.read(unzippedLink))
        }
      }

      test("zipStreamPreserveLinks") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, zipStream = true, followLinks = false)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)
          // test all permissions are preserved
          assert(os.walk.stream(source)
            .filter(!os.isLink(_))
            .forall(p => os.perms(p) == os.perms(unzipped / p.relativeTo(source))))

          // test symlinks are zipped as symlinks
          val unzippedLink = unzipped / link
          assert(os.isLink(unzippedLink))
          assert(os.readLink(source / link) == os.readLink(unzippedLink))
        }
      }

      test("unzipStreamWithLinks") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, unzipStream = true, followLinks = false)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)

          val unzippedLink = unzipped / link
          assert(os.isFile(unzippedLink))
          assert(os.readLink(source / link).toString == os.read(unzippedLink))
        }
      }

      test("unzipStream") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd, unzipStream = true, followLinks = true)

          // test all files are there
          assert(walkRel(source).toSet == walkRel(unzipped).toSet)

          // test symlinks zipped as the referenced files are unzipped correctly
          val unzippedLink = unzipped / link
          assert(os.isFile(unzippedLink))
          assert(os.read(os.readLink.absolute(source / link)) == os.read(unzippedLink))
        }
      }

      test("existingZip") - prep { wd =>
        if (!scala.util.Properties.isWin) {
          val (source, unzipped, link) = prepare(wd)

          val newSource = os.pwd / "source"
          os.makeDir(newSource)

          val newDir = newSource / "new" / "dir"
          os.makeDir.all(newDir)
          os.perms.set(newDir, os.PermSet.fromString("rwxrwxrwx"))
          os.write.over(newDir / "a.txt", "Contents of a.txt")

          val newFile = os.sub / "new.txt"
          val perms = os.PermSet.fromString("rw-rw-rw-")
          os.write(newSource / newFile, "Contents of new.txt")
          os.perms.set(newSource / newFile, perms)

          val newLink = os.sub / "newLink.txt"
          os.symlink(newSource / newLink, os.rel / "new.txt")

          val newZipped = os.zip(
            dest = wd / "zipped.zip",
            sources = List(newSource)
          )

          val newUnzipped = os.unzip(
            source = newZipped,
            dest = wd / "newUnzipped"
          )

          // test all files are there
          assert((walkRel(source) ++ walkRel(newSource)).toSet == walkRel(newUnzipped).toSet)

          // test permissions of existing zip entries are preserved
          if (Runtime.version.feature >= 14) {
            assert(os.walk.stream(source)
              .filter(!os.isLink(_))
              .forall(p => os.perms(p) == os.perms(newUnzipped / p.relativeTo(source))))
          }

          // test existing symlinks zipped as the referenced files are unzipped
          val unzippedNewLink = newUnzipped / newLink
          assert(os.isFile(unzippedNewLink))
          assert(os.read(os.readLink.absolute(newSource / newLink)) == os.read(unzippedNewLink))

          // test permissions of newly added files are preserved
          val unzippedNewFile = newUnzipped / newFile
          if (Runtime.version.feature >= 14) {
            assert(os.perms(unzippedNewFile) == perms)
            assert(os.perms(unzippedNewLink) == perms)
          }
        }
      }
    }

    test("zipSymlink") - prep { wd =>
      val zipFileName = "zipped.zip"
      val source = wd / "folder1"
      val linkName = "link.txt"
      val link = os.rel / linkName

      os.symlink(source / link, os.rel / "one.txt")

      val zipped = os.zip(
        dest = wd / zipFileName,
        sources = List(source),
        followLinks = false
      )

      val unzipped = os.unzip(
        source = zipped,
        dest = wd / "unzipped"
      )

      import os.{shaded_org_apache_tools_zip => apache}
      val zipFile = new apache.ZipFile(zipped.toIO)
      val entry = zipFile.getEntry(linkName)

      // check if zipped correctly as symlink
      assert(
        (entry.getUnixMode & apache.PermissionUtils.FILE_TYPE_FLAG) == apache.UnixStat.LINK_FLAG
      )
      assert(os.isLink(unzipped / link))
      assert(os.readLink(unzipped / link) == os.readLink(source / link))
    }

    test("unzipStream") - prep { wd =>
      // Step 1: Create an in-memory ZIP file as a stream
      val zipStreamOutput = new ByteArrayOutputStream()
      val zipOutputStream = new ZipOutputStream(zipStreamOutput)

      // Step 2: Add some files to the ZIP
      val file1Name = "file1.txt"
      val file2Name = "nested/folder/file2.txt"

      // Add first file
      zipOutputStream.putNextEntry(new ZipEntry(file1Name))
      zipOutputStream.write("Content of file1".getBytes)
      zipOutputStream.closeEntry()

      // Add second file inside a nested folder
      zipOutputStream.putNextEntry(new ZipEntry(file2Name))
      zipOutputStream.write("Content of file2".getBytes)
      zipOutputStream.closeEntry()

      // Close the ZIP output stream
      zipOutputStream.close()

      // Step 3: Prepare the destination folder for unzipping
      val unzippedFolder = wd / "unzipped-stream-folder"
      val readableZipStream: java.io.InputStream =
        new ByteArrayInputStream(zipStreamOutput.toByteArray)

      // Unzipping the stream to the destination folder
      os.unzip.stream(
        source = readableZipStream,
        dest = unzippedFolder
      )

      // Step 5: Verify the unzipped files and contents
      val paths = os.walk(unzippedFolder)
      assert(paths.contains(unzippedFolder / file1Name))
      assert(paths.contains(unzippedFolder / "nested" / "folder" / "file2.txt"))

      // Check the contents of the files
      val file1Content = os.read(unzippedFolder / file1Name)
      val file2Content = os.read(unzippedFolder / "nested" / "folder" / "file2.txt")

      assert(file1Content == "Content of file1")
      assert(file2Content == "Content of file2")
    }

    test("unzipDirectoryEnsureExecutablePermission") - prep { wd =>
      if (!scala.util.Properties.isWin) {
        val zipFileName = "zipDirExecutable"
        val source = wd / "folder1"
        val dir = source / "dir"

        os.makeDir(dir)
        val perms = os.perms(dir)
        os.perms.set(dir, perms - PosixFilePermission.OWNER_EXECUTE)

        val zipped = os.zip(
          dest = wd / s"$zipFileName.zip",
          sources = Seq(source)
        )

        val unzipped = os.unzip(zipped, dest = wd / zipFileName)
        assert(os.perms(unzipped / "dir").contains(PosixFilePermission.OWNER_EXECUTE))
        assert(os.perms(unzipped / "dir") == perms)
      }
    }
  }
}
