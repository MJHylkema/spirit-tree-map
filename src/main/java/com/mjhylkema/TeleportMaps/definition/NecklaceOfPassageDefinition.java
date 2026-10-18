package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class NecklaceOfPassageDefinition
{
  @Getter
  static private final int width = 24;
  @Getter
  static private final int height = 22;

  private String name;
  private int x;
  private int y;
  private HotKeyDefinition hotkey;
  private TravelOptionDefinition[] travelOptions;
}
