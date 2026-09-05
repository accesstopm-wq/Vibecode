G := AlternatingGroup(5);
Print("A5 elements: ", Size(G), "\n\n");
for g in Elements(G) do
  Print(g, "\n");
od;
