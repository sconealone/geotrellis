package geotrellis

import geotrellis.spark.formats._
import geotrellis.spark.metadata.Context
import geotrellis.spark.rdd.RasterRDD
import geotrellis.spark.rdd.SaveRasterFunctions

import org.apache.hadoop.fs.Path
import org.apache.spark.rdd.RDD

package object spark {
  implicit class SavableRasterWritable(val raster: RDD[WritableTile]) {
    def save(path: Path) = SaveRasterFunctions.save(raster, path)
  }

  implicit class MakeRasterRDD(val prev: RDD[Tile]) {
    def withContext(ctx: Context) = new RasterRDD(prev, ctx)
  }

  implicit class SavableRasterRDD(val rdd: RasterRDD) {
    def save(path: Path) = SaveRasterFunctions.save(rdd, path)
  }
}
