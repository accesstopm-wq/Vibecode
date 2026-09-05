SetUserPreference("UseColorPrompt", false);;
SetUserPreference("UseColorsInTerminal", false);;
ColorPrompt(false);;

G := GL(3,2);;

id := IdGroup(G);;
H := SmallGroup(id[1], id[2]);;

Print("GL(3,2) = ", StructureDescription(G), "\n");
Print("IdGroup = ", id, "\n");
Print("SmallGroup(168,42) = ", H, "\n");
Print("Generators of G: ", GeneratorsOfGroup(G), "\n");
Print("All elements of G: ", Elements(G), "\n");
Print("Generators of H: ", GeneratorsOfGroup(H), "\n");
Print("All elements of H: ", Elements(H), "\n");
