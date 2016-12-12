package geotrellis.spark.pointcloud.triangulation

import io.pdal._
import geotrellis.spark.pointcloud._
import geotrellis.raster._
import geotrellis.spark._
import geotrellis.spark.tiling._
import geotrellis.vector._

import org.apache.spark.rdd.RDD
import spire.syntax.cfor._

import scala.collection.mutable

object TinToDem {
  case class Options(
    cellType: CellType = DoubleConstantNoDataCellType,
    boundsBuffer: Option[Double] = None
  )

  object Options {
    def DEFAULT = Options()
  }

  def apply(rdd: RDD[(SpatialKey, Array[Point3D])], layoutDefinition: LayoutDefinition, extent: Extent, options: Options = Options.DEFAULT): RDD[(SpatialKey, Tile)] =
    rdd
      .collectNeighbors
      .mapPartitions({ partition =>
        partition.flatMap { case (key, neighbors) =>
          val extent @ Extent(xmin, ymin, xmax, ymax) =
            layoutDefinition.mapTransform(key)

          var len = 0
          neighbors.foreach { case (_, (_, arr)) =>
            len += arr.length
          }
          val points = Array.ofDim[Point3D](len)
          var j = 0
          options.boundsBuffer match {
            case Some(b) =>
              val bufferedExtent =
                Extent(
                  xmin - b,
                  ymin - b,
                  xmax + b,
                  ymax + b
                )

              neighbors.foreach { case (_, (_, tilePoints)) =>
                cfor(0)(_ < tilePoints.length, _ + 1) { i =>
                  val p = tilePoints(i)
                  // Only consider points within `boundsBuffer` of the extent
                  if(bufferedExtent.contains(p.x, p.y)) {
                    points(j) = p
                    j += 1
                  }
                }
              }
            case None =>
              neighbors.foreach { case (_, (_, tilePoints)) =>
                cfor(0)(_ < tilePoints.length, _ + 1) { i =>
                  points(j) = tilePoints(i)
                  j += 1
                }
              }
          }

          if(j > 2) {
            val pointSet =
              new DelaunayPointSet {
                def length: Int = j
                def getX(i: Int): Double = points(i).x
                def getY(i: Int): Double = points(i).y
                def getZ(i: Int): Double = points(i).z
              }

            val delaunay =
              PointCloudTriangulation(pointSet)

            val re =
              RasterExtent(
                extent,
                layoutDefinition.tileCols,
                layoutDefinition.tileRows
              )

            val tile =
              ArrayTile.empty(options.cellType, re.cols, re.rows)

            delaunay.rasterize(tile, re)

            Some((key, tile))
          } else {
            None
          }
        }
      }, preservesPartitioning = true)

  // def withStitch(rdd: RDD[(SpatialKey, Array[Point3D])], layoutDefinition: LayoutDefinition, extent: Extent, options: Options = Options.DEFAULT): RDD[(SpatialKey, Tile)] = {

  //   // Assumes that a partitioner has already been set

  //   val trianglations: RDD[(SpatialKey, PointCloudTriangulation)] =
  //     rdd
  //       .mapValues { points =>
  //         PointCloudTriangulation(points)
  //       }

  //   val borders: RDD[(SpatialKey, BorderTriangulation)] =
  //     triangluations
  //       .mapValues(_.convertToBorder)

  //   borders
  //     .collectNeighbors
  //     .mapPartitions({ paritions =>
  //       partitions.map { case (key, neighbors) =>
  //         val newNeighbors =
  //           neighbors.map { case (direction, (key2, border))
  //             val extent = layoutDefinition.mapTransform(key2)
  //             (key, (direction, (extent, border)))
  //           }
  //         (key, newNeighbors)
  //       }
  //     }, preservesPartitioning = true)
  //     .join(triangulations)
  //     .mapValues { case (borders: Seq[(Direction, (Extent, BorderTriangulation))], triangulation) =>
  //       val stitched = triangulation.stitch(borders)

  //       val extent = layoutDefinition.mapTransform(key)
  //       val re =
  //         RasterExtent(
  //           extent,
  //           layoutDefinition.tileCols,
  //           layoutDefinition.tileRows
  //         )

  //         val tile =
  //           ArrayTile.empty(options.cellType, re.cols, re.rows)

  //         stitched.rasterize(tile, re)

  //         (key, tile)
  //     }
  // }
}