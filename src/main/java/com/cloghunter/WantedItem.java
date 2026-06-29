package com.cloghunter;

class WantedItem
{
	private final int itemId;
	private final String name;
	private final int quantity;
	private final String activity;
	private final String category;
	private final String dropRate;
	private final boolean obtained;

	WantedItem(int itemId, String name, int quantity, String activity, String category, String dropRate, boolean obtained)
	{
		this.itemId = itemId;
		this.name = name;
		this.quantity = quantity;
		this.activity = activity;
		this.category = category;
		this.dropRate = dropRate;
		this.obtained = obtained;
	}

	int getItemId()
	{
		return itemId;
	}

	String getName()
	{
		return name;
	}

	int getQuantity()
	{
		return quantity;
	}

	String getActivity()
	{
		return activity;
	}

	String getCategory()
	{
		return category;
	}

	String getDropRate()
	{
		return dropRate;
	}

	boolean isObtained()
	{
		return obtained;
	}
}
