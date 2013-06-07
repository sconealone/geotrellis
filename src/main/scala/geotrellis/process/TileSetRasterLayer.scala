package geotrellis.process

import geotrellis._
import geotrellis.util._
import geotrellis.data.arg.ArgReader
import geotrellis.raster.{TileSetRasterData,
                          TileLayout,
                          Tiler,
                          CroppedRaster,
                          IntConstant}

import com.typesafe.config.Config
import java.io.File

import spire.syntax._

object TileSetRasterLayerBuilder
extends RasterLayerBuilder {
  def apply(jsonPath:String, json:Config, cache:Option[Cache]):Option[RasterLayer] = {
    val tileDir = 
      if(json.hasPath("path")) {
        val f = new File(json.getString("path"))
        if(f.isAbsolute) {
          f
        } else {
          new File(new File(jsonPath).getParent, f.getPath)
        }
      } else {
        // Default to a directory with the same base name as the .json file.
        new File(new File(jsonPath).getParent, getName(json))
      }

    if(!tileDir.isDirectory) {
      System.err.println(s"[ERROR] Raster in catalog points Tile Directory '${tileDir.getPath}'" +
                          ", but this is not a valid directory.")
      System.err.println("[ERROR]   Skipping this raster layer...")
      None
    } else {
      val tileDirPath = tileDir.getPath
      val layoutCols = json.getInt("layout_cols")
      val layoutRows = json.getInt("layout_rows")
      val pixelCols = json.getInt("pixel_cols")
      val pixelRows = json.getInt("pixel_rows")
      val cols = layoutCols * pixelCols
      val rows = layoutRows * pixelRows

      val (cw,ch) = getCellWidthAndHeight(json)

      val rasterExtent = RasterExtent(getExtent(json), cw, ch, cols, rows)
      val layout = TileLayout(layoutCols, layoutRows, pixelCols, pixelRows)

      val info = RasterLayerInfo(getName(json),
                                 getRasterType(json),
                                 rasterExtent,
                                 getEpsg(json),
                                 getXskew(json),
                                 getYskew(json))

      Some(new TileSetRasterLayer(info,tileDirPath,layout,cache))
    }
  }
}

class TileSetRasterLayer(info:RasterLayerInfo, 
                         val tileDirPath:String,
                         val tileLayout:TileLayout,
                         c:Option[Cache])
extends RasterLayer(info,c) {
  def getRaster(targetExtent:Option[RasterExtent]) = {
    targetExtent match {
      case Some(re) =>
        // If a specific raster extent is asked for,
        // load an ArrayRasterData for the extent.
        // TODO: Is this the best strategy? Or should
        // tile rasters loaded with different extents\resolutions
        // produce tiled rasters?

        // Create destination raster data
        val data = RasterData.emptyByType(info.rasterType,re.cols,re.rows)

        // Collect data from intersecting tiles
        val targetExtent = re.extent
        val resLayout = tileLayout.getResolutionLayout(info.rasterExtent)
        cfor(0)( _ < tileLayout.tileCols, _ + 1) { tcol =>
          cfor(0)( _ < tileLayout.tileRows, _ + 1) { trow =>
            val sourceRasterExtent = resLayout.getRasterExtent(tcol,trow)
            val sourceExtent = resLayout.getExtent(tcol,trow)
            sourceExtent.intersect(targetExtent) match {
              case Some(ext) =>
                val cols = math.ceil((ext.xmax - ext.xmin) / re.cellwidth).toInt
                val rows = math.ceil((ext.ymax - ext.ymin) / re.cellheight).toInt
                // Resize extent to fit cell width and height
                val e = Extent(ext.xmin,
                               ext.ymax - (rows*re.cellheight),
                               ext.xmin + (cols*re.cellwidth),
                               ext.ymax)
                val tileRe = RasterExtent(e,re.cellwidth,re.cellheight,cols,rows)

                // Read section of the tile
                val path = Tiler.tilePath(tileDirPath, info.name, tcol, trow)
                val sourceRasterExtent = resLayout.getRasterExtent(tcol,trow)
                val rasterPart = 
                  new ArgReader(path).readPath(info.rasterType,sourceRasterExtent,tileRe)

                // Copy over the values to the correct place in the raster data
                cfor(0)(_ < cols, _ + 1) { partCol =>
                  cfor(0)(_ < rows, _ + 1) { partRow =>
                    val dataCol = re.mapXToGrid(tileRe.gridColToMap(partCol))
                    val dataRow = re.mapYToGrid(tileRe.gridRowToMap(partRow))
                    if(!(dataCol < 0 || dataCol >= re.cols ||
                         dataRow < 0 || dataRow >= re.rows)) {
                      if(info.rasterType.isDouble) {
                        data.setDouble(dataCol, dataRow, rasterPart.getDouble(partCol, partRow))
                      } else {
                        data.set(dataCol, dataRow, rasterPart.get(partCol, partRow))
                      }
                    }
                  }
                }

              case None => // pass
            }
          }
        }
        Raster(data, re)
      case None => 
        Raster(getData, info.rasterExtent)
    }
  }

  def getData() = 
    TileSetRasterData(tileDirPath,
                      info.name,
                      info.rasterType,
                      tileLayout,
                      getTileLoader)

  def getTileLoader() =
    new TileLoader(tileDirPath,info,tileLayout)

  def cache() = {} // TODO: Implement
}

class TileLoader(tileDirPath:String,
                 tileSetInfo:RasterLayerInfo,
                 tileLayout:TileLayout) {
  val resLayout = tileLayout.getResolutionLayout(tileSetInfo.rasterExtent)

  val rasterExtent = tileSetInfo.rasterExtent

  def getTile(col:Int,row:Int):Raster = {
    val re = resLayout.getRasterExtent(col,row)
    if(col < 0 || row < 0 ||
       tileLayout.tileCols <= col || tileLayout.tileRows <= row) {
      Raster(IntConstant(NODATA, rasterExtent.cols, rasterExtent.rows),  rasterExtent)
    } else {
      val path = Tiler.tilePath(tileDirPath, tileSetInfo.name, col, row)
      new ArgReader(path).readPath(tileSetInfo.rasterType,re,re)
    }
  }
}