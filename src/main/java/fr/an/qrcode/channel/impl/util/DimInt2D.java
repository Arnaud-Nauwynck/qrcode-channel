package fr.an.qrcode.channel.impl.util;

public class DimInt2D {
	public final int w;
	public final int h;
	
	public DimInt2D(int w, int h) {
		this.w = w;
		this.h = h;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + h;
		result = prime * result + w;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DimInt2D other = (DimInt2D) obj;
		if (h != other.h)
			return false;
		if (w != other.w)
			return false;
		return true;
	}


	@Override
	public String toString() {
		return w + "x" + h;
	}
	
}
